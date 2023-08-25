(ns fmnoise.coldbrew
  {:clj-kondo/config '{:lint-as {fmnoise.coldbrew/defached clojure.core/defn}}}
  (:import (com.github.benmanes.caffeine.cache Cache CacheLoader LoadingCache Caffeine RemovalListener Scheduler)
           (java.time Duration)
           (java.util.concurrent Executor)
           (java.util.function Function))
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(def ^:private cache-options
  #{:expire
    :expire-after-access
    :initial-capacity
    :scheduler
    :executor
    :refresh
    :when
    :max-size
    :weak-keys
    :weak-values
    :soft-values
    :eviction-listener
    :removal-listener})

(defn- check-cache-options [options]
  (let [diff (set/difference (some-> options keys set) cache-options)]
    (when (seq diff)
      (throw (IllegalArgumentException. (str "Unsupported caching options: " (str/join "," diff))))))
  (when (and (:soft-values options) (:weak-values options))
    (throw (IllegalArgumentException. "Only soft-values or weak-values options can be used "))))

(defn- cache-loader ^CacheLoader [f]
  (reify CacheLoader (load [_ key] (apply f key))))

(defn build-cache
  "Builds Caffeine Loading Cache with given options and optional cache function. Supported options are:
  `:expire` - expiration time after write (in seconds)
  `:expire-after-access` - expiration time after access (in seconds)
  `:initial-capacity` - initial capacity of internal data structure
  `:scheduler` - Routine cache maintenance scheduler
  `:executor` - Executor for `:removal-listener`, periodic maintenance or refreshes
  `:refresh` - refresh time (in seconds)
  `:when` - function for checking if value should be cached
  `:max-size` - number, sets maximum cache size
  `:weak-keys` - boolean, switch cache to using weak references for keys
  `:weak-values` - boolean, switch cache to using weak references for values (can't be set together with `:soft-values`)
  `:soft-values` - boolean, switch cache to using soft references for values (can't be set together with `:weak-values`)
  `:eviction-listener` - function of three parameters (key, value, cause) that is called synchronously when a cache entry is evicted (due to policy)
  `:removal-listener` - function of three parameters (key, value, cause) that is called asynchronously (using `:executor`, if given) when a cache entry is removed (invalidated or evicted)
  "
  [options & [cache-fn]]
  (let [_ (check-cache-options options)
        {:keys [expire expire-after-access initial-capacity scheduler executor refresh max-size weak-keys weak-values soft-values eviction-listener removal-listener]} options
        ^Caffeine cache-builder (cond-> (Caffeine/newBuilder)
                                  expire
                                  (.expireAfterWrite (Duration/ofSeconds expire))
                                  expire-after-access
                                  (.expireAfterAccess (Duration/ofSeconds expire-after-access))
                                  initial-capacity
                                  (.initialCapacity (int initial-capacity))
                                  scheduler
                                  (.scheduler ^Scheduler scheduler)
                                  executor
                                  (.executor ^Executor executor)
                                  refresh
                                  (.refreshAfterWrite (Duration/ofSeconds refresh))
                                  max-size
                                  (.maximumSize ^long max-size)
                                  weak-keys
                                  (.weakKeys)
                                  weak-values
                                  (.weakValues)
                                  soft-values
                                  (.softValues)
                                  eviction-listener
                                  (.evictionListener
                                   (reify RemovalListener
                                     (onRemoval [_ key value cause]
                                       (eviction-listener key value cause))))
                                  removal-listener
                                  (.removalListener
                                   (reify RemovalListener
                                     (onRemoval [_ key value cause]
                                       (removal-listener key value cause)))))]
    (if cache-fn
      (.build cache-builder (cache-loader cache-fn))
      (.build cache-builder))))

(defn put
  "Insert/update a cache entry."
  [^Cache cache key val]
  (.put cache key val))

(defn put-all
  "Insert/update multiple cache entries.
  `m` is a map of key -> value."
  [^Cache cache m]
  (.putAll cache m))

(defn invalidate
  "Invalidate a cache entry."
  [^Cache cache key]
  (.invalidate cache key))

(defn lookup
  "Performs cache lookup. Accepts optional function which uses cache key to calculate missing value"
  ([^Cache cache key]
   (if (instance? LoadingCache cache)
     (.get ^LoadingCache cache key)
     (.getIfPresent cache key)))
  ([^Cache cache key f]
   (.get cache key (reify Function (apply [_ key] (f key))))))

(defn lookup-some
  "Performs cache lookup for existing value"
  [^Cache cache key] (.getIfPresent cache key))

(defn cond-lookup
  "Performs cache lookup and conditional value computation if value is missing"
  [^Cache cache key condition-fn computation-fn & [args]]
  (or (.getIfPresent cache key)
      (let [computed (apply computation-fn args)]
        (when (condition-fn computed)
          (.put cache key computed))
        computed)))

(defn cached
  "Accepts a function and creates cached version of it which uses Caffeine Loading Cache.
  Cache options can be provided as meta to function. See `build-cache` documentation for the list of supported-options.
  Examples:
  (cached ^{:expire 86400 :refresh 36000}
   (fn [db id] (query db id)))

  ;; calculate todays order capacity until it reaches zero
  (cached ^{:when zero?}
   (fn [db worker-id] (order-capacity db worker-id)))
  "
  [f]
  (let [options (meta f)
        condition-fn (-> f meta :when)
        cache (build-cache options (when-not condition-fn f))]
    (fn [& args]
      (let [key (or args [])]
        (if condition-fn
          (cond-lookup cache key condition-fn f args)
          (lookup cache key))))))

(defmacro defcached
  "Creates a function which uses Caffeine Loading Cache under the hood.
  Function declaration is similar to defn:
  - name (symbol) with optional meta
  - docstring (optional)
  - args (vector)
  - pre/post conditions map (optional)
  - caching key (vector) with optional meta containing cache options (see `cached` for more details)
  - body

  Example:
  (defcached customer-lifetime-value [db date {:customer/keys [id]}]
   ^{:expire 86400 :refresh 36000}
   [db date id]
   (query-customer-ltv db date id))
  "
  [name & fdecl]
  (let [fname (gensym (str name "-cached-"))
        [?docstring ?args ?prepost ?cache-key] fdecl
        docstring (when (string? ?docstring) ?docstring)
        args (if docstring ?args ?docstring)
        prepost (cond
                  (and docstring (map? ?prepost)) ?prepost
                  (and (nil? docstring) (map? ?args)) ?args)
        cache-key (cond
                    (and docstring prepost) ?cache-key
                    (and (nil? docstring) (nil? prepost)) ?args
                    :else ?prepost)
        cache-options (-> cache-key meta (dissoc :file :end-column :column :line :end-line))
        _ (check-cache-options cache-options)
        body (nthrest fdecl (cond
                              (and docstring prepost) 4
                              (and (nil? docstring) (nil? prepost)) 2
                              :else 3))
        fdefn (filter some? (list name docstring args prepost))]
    `(do
      (def ^:private ~fname
         (cached
          (with-meta
            (fn [~@cache-key]
              ~@body)
            ~cache-options)))
      (defn ~@fdefn (~fname ~@cache-key))
      (vary-meta ~name merge ~(meta name)))))
