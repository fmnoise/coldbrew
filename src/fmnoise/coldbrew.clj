(ns fmnoise.coldbrew
  (:import (com.github.benmanes.caffeine.cache Cache CacheLoader LoadingCache Caffeine)
           (java.time Duration)))

(defn cached
  "Accepts a function and creates cached version of it which uses Caffeine Loading Cache.
  Cache options can be provided as meta to function. Supported options are:
  `:expire` - expiration time (in seconds)
  `:refresh` - refresh time (in seconds)
  `:when` - function for checking if value should be cached

  Examples:
  (cached ^{:expire 86400 :refresh 36000}
   (fn [db id] (query db id)))

  ;; calculate todays order capacity until it reaches zero
  (cached ^{:when zero?}
   (fn [db worker-id] (order-capacity db worker-id)))
  "
  [f]
  (let [^Caffeine cache-builder (Caffeine/newBuilder)
        condition-fn (-> f meta :when)
        _ (when-let [expire (-> f meta :expire)]
            (.expireAfterWrite cache-builder (Duration/ofSeconds expire)))
        _ (when-let [refresh (-> f meta :refresh)]
            (.refreshAfterWrite cache-builder (Duration/ofSeconds refresh)))
        cache (if condition-fn
                (.build cache-builder)
                (.build cache-builder (reify CacheLoader (load [_ key] (apply f key)))))]
    (if condition-fn
      (fn [& args]
        (let [key (or args [])]
          (or (.getIfPresent ^Cache cache key)
              (let [computed (apply f args)]
                (when (condition-fn computed)
                  (.put ^Cache cache key computed))
                computed))))
      (fn [& args]
        (.get ^LoadingCache cache (or args []))))))

(defmacro defcached
  "Creates a function which uses Caffeine Loading Cache under the hood.
  Function declaration is similar to defn:
  - name (symbol) with optional meta
  - args (vector)
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
            ~(meta cache-key))))
      (defn ~@fdefn (~fname ~@cache-key))
      (vary-meta ~name merge ~(meta name)))))
