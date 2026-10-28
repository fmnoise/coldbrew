(ns fmnoise.coldbrew-test
  (:require [clojure.test :refer [deftest is]]
            [fmnoise.coldbrew :as c :refer [defcached cached]])
  (:import (com.github.benmanes.caffeine.cache Cache Ticker Scheduler)
           (java.util.concurrent CountDownLatch TimeUnit Executor)))

(defn- test-clock []
  (let [nanos (atom 0)]
    {:ticker (reify Ticker (read [_] @nanos))
     :advance! #(swap! nanos + (* % 1000000000))}))

(deftest cached-fn-test
  (let [{:keys [ticker advance!]} (test-clock)
        counter (atom 0)
        cf (cached (with-meta (fn [a b] (swap! counter inc) (+ a b)) {:expire 3 :ticker ticker}))
        r1 (cf 1 2)
        c1 @counter
        r2 (cf 1 2)
        c2 @counter
        _ (advance! 3)
        r3 (cf 1 2)
        c3 @counter
        r4 (cf 2 3)
        c4 @counter]
    (is (= 3 r1 r2 r3) "function produces correct result")
    (is (= 1 c1 c2) "counter is affected only on first run")
    (is (= 2 c3) "counter is affected after expiration")
    (is (= 5 r4) "function produces correct result")
    (is (= 3 c4) "counter is affected after calling with different arguments")))

(deftest cache-when-test
  (let [store (atom 3)
        cf (cached (with-meta (fn [] (swap! store dec)) {:when zero?}))
        r1 (cf)
        r2 (cf)
        r3 (cf)
        r4 (cf)]
    (is (= 2 r1) "function produces correct result")
    (is (= 1 r2) "function produces correct result")
    (is (= 0 r3) "function produces correct result")
    (is (= 0 r4) "function returns cached value which satisfied condition")
    (is (= 0 @store) "function body isn't called")))

(deftest defcached-test
  (let [{:keys [ticker advance!]} (test-clock)
        counter (atom 0)]
    (defcached cached-func [a b]
      ^{:expire 3 :ticker ticker}
      [a b]
      (swap! counter inc)
      (+ a b))
    (let [r1 (cached-func 1 2)
          c1 @counter
          r2 (cached-func 1 2)
          c2 @counter
          _ (advance! 3)
          r3 (cached-func 1 2)
          c3 @counter
          r4 (cached-func 2 3)
          c4 @counter]
      (is (= 3 r1 r2 r3) "function produces correct result")
      (is (= 1 c1 c2) "counter is affected only on first run")
      (is (= 2 c3) "counter is affected after expiration")
      (is (= 5 r4) "function produces correct result")
      (is (= 3 c4) "counter is affected after calling with different arguments"))))

(deftest defcached-when-test
  (let [store (atom 3)]
    (defcached cached-func []
      ^{:when zero?} []
      (swap! store dec))
    (let [r1 (cached-func)
          r2 (cached-func)
          r3 (cached-func)
          r4 (cached-func)]
      (is (= 2 r1) "function produces correct result")
      (is (= 1 r2) "function produces correct result")
      (is (= 0 r3) "function produces correct result")
      (is (= 0 r4) "function returns cached value which satisfied condition")
      (is (= 0 @store) "function body isn't called"))))

(deftest defcached-declaration-test
  (defcached f1 [a b]
      ^{:expire 10} [a b]
      (- a b)
      (+ a b))
  (defcached f2 "adds a and b" [a b]
      ^{:expire 10} [a b]
      (- a b)
      (+ a b))
  (defcached f3 [a b]
    {:pre [(pos? a) (pos? b)]}
    ^{:expire 10} [a b]
    (- a b)
    (+ a b))
  (defcached f4 "adds a and b" [a b]
    {:pre [(pos? a) (pos? b)]}
    ^{:expire 10} [a b]
    (- a b)
    (+ a b))
  (is (= 3 (f1 1 2) (f2 1 2) (f3 1 2) (f4 1 2)) "function produces correct result")
  (is (= "adds a and b" (:doc (meta #'f2)) (:doc (meta #'f4))) "docstring is added to function meta")
  (is (thrown? AssertionError (f3 0 0)) "pre-conditions are added to function")
  (is (thrown? AssertionError (f4 0 0)) "pre-conditions are added to function"))

(deftest conditional-values-test
  (doseq [value [false nil 0]]
    (let [calls (atom 0)
          f (cached (with-meta (fn [] (swap! calls inc) value)
                      {:when (constantly true)}))]
      (is (= value (f) (f)))
      (is (= (if (nil? value) 2 1) @calls))))
  (let [calls (atom 0)
        f (cached (fn [] (swap! calls inc) false))]
    (is (false? (f)))
    (is (false? (f)))
    (is (= 1 @calls))))

(deftest cache-api-test
  (let [cache (c/build-cache {})]
    (is (nil? (c/lookup cache :missing)))
    (c/put cache :a false)
    (is (false? (c/lookup cache :a)))
    (c/put-all cache {:a 1 :b 2})
    (is (= 1 (c/lookup-some cache :a)))
    (is (= 2 (c/lookup cache :b)))
    (c/invalidate cache :a)
    (is (nil? (c/lookup-some cache :a)))
    (is (= :missing (c/lookup cache :missing identity)))
    (is (= :missing (c/lookup cache :missing (fn [_] (throw (Exception. "Unexpected load")))))))
  (let [calls (atom 0)
        cache (c/build-cache {} (fn [a b] (swap! calls inc) (+ a b)))]
    (is (nil? (c/lookup-some cache [1 2])))
    (is (zero? @calls))
    (is (= 3 (c/lookup cache [1 2]) (c/lookup cache [1 2])))
    (is (= 1 @calls))
    (c/invalidate cache [1 2])
    (is (= 3 (c/lookup cache [1 2])))
    (is (= 2 @calls))))

(deftest option-validation-test
  (doseq [options [{:unknown true}
                   {:weak-values true :soft-values true}
                   {:expire -1}
                   {:max-size -1}
                   {:initial-capacity -1}
                   {:when pos? :refresh 1}]]
    (is (thrown? IllegalArgumentException (c/build-cache options))))
  (is (thrown-with-msg? IllegalArgumentException #"requires a cache function"
                       (c/build-cache {:refresh 1})))
  (is (thrown-with-msg? IllegalArgumentException #":weak-keys"
                       (cached (with-meta identity {:weak-keys true})))))

(deftest expiration-after-access-test
  (let [{:keys [ticker advance!]} (test-clock)
        cache (c/build-cache {:expire-after-access 3 :ticker ticker})]
    (c/put cache :key 42)
    (advance! 2)
    (is (= 42 (c/lookup cache :key)))
    (advance! 2)
    (is (= 42 (c/lookup cache :key)))
    (advance! 3)
    (is (nil? (c/lookup cache :key)))))

(deftest listeners-and-eviction-test
  (let [removed (atom [])
        evicted (atom [])
        ^Cache cache (c/build-cache
                {:initial-capacity 1 :max-size 1
                 :scheduler (reify Scheduler
                              (schedule [_ _ _ _ _]
                                (java.util.concurrent.CompletableFuture/completedFuture nil)))
                 :executor (reify Executor (execute [_ task] (.run ^Runnable task)))
                 :removal-listener #(swap! removed conj [%1 %2 %3])
                 :eviction-listener #(swap! evicted conj [%1 %2 %3])})]
    (c/put cache :a 1)
    (c/put cache :b 2)
    (.cleanUp cache)
    (is (= 1 (.estimatedSize cache)))
    (is (= 1 (count @evicted)))
    (is (.wasEvicted (nth (first @evicted) 2)))
    (let [key (if (some? (c/lookup-some cache :a)) :a :b)]
      (c/invalidate cache key)
      (.cleanUp cache)
      (is (= 2 (count @removed)))
      (is (= "EXPLICIT" (str (nth (last @removed) 2))))
      (is (= 1 (count @evicted))))))

(deftest refresh-test
  (let [{:keys [ticker advance!]} (test-clock)
        calls (atom 0)
        ^Cache cache (c/build-cache
                {:refresh 1 :ticker ticker
                 :executor (reify Executor (execute [_ task] (.run ^Runnable task)))}
                (fn [_] (swap! calls inc)))]
    (is (= 1 (c/lookup cache [:key])))
    (advance! 2)
    (c/lookup cache [:key])
    (is (= 2 @calls))
    (is (= 2 (c/lookup cache [:key])))))

(deftest concurrent-conditional-test
  (let [calls (atom 0)
        entered (promise)
        release (promise)
        ready (CountDownLatch. 8)
        start (promise)
        f (cached (with-meta
                    (fn [key]
                      (swap! calls inc)
                      (deliver entered true)
                      (when (= ::timeout (deref release 5000 ::timeout))
                        (throw (ex-info "Timed out waiting for release" {})))
                      key)
                    {:when some?}))
        workers (doall (repeatedly 8
                         #(future (.countDown ready) @start (f :key))))]
    (try
      (is (.await ready 5 TimeUnit/SECONDS))
      (deliver start true)
      (is (= true (deref entered 5000 ::timeout)))
      (deliver release true)
      (is (= (repeat 8 :key) (mapv #(deref % 5000 ::timeout) workers)))
      (is (= 1 @calls))
      (finally
        (deliver start true)
        (deliver release true)
        (doseq [worker workers] (future-cancel worker))))))

(deftest exceptions-are-not-cached-test
  (doseq [options [{} {:when some?}]]
    (let [calls (atom 0)
          f (cached (with-meta
                      (fn [] (if (= 1 (swap! calls inc))
                               (throw (ex-info "First call fails" {}))
                               :ok))
                      options))]
      (is (thrown? Exception (f)))
      (is (= :ok (f) (f)))
      (is (= 2 @calls)))))

(deftest defcached-key-and-contract-test
  (let [calls (atom 0)]
    (defcached ^:private keyed-function
      "Uses destructured bindings in the cache key."
      [{:keys [base-url timeout]} id ignored]
      {:pre [(pos? timeout)] :post [(string? %)]}
      [base-url timeout id]
      (swap! calls inc)
      (str base-url "/" id "@" timeout))
    (is (= "https://example.com/1@10"
           (keyed-function {:base-url "https://example.com" :timeout 10} 1 :a)
           (keyed-function {:base-url "https://example.com" :timeout 10} 1 :b)))
    (is (= 1 @calls))
    (is (:private (meta #'keyed-function)))
    (is (= "Uses destructured bindings in the cache key." (:doc (meta #'keyed-function))))
    (is (thrown? AssertionError (keyed-function {:timeout 0} 1 nil))))
  (defcached invalid-postcondition [x]
    {:post [(pos? %)]}
    [x]
    x)
  (is (thrown? AssertionError (invalid-postcondition -1)))
  (is (thrown? AssertionError (invalid-postcondition -1))))

(deftest nil-results-are-not-cached-test
  (let [calls (atom 0)
        f (cached (fn [] (swap! calls inc) nil))]
    (is (nil? (f)))
    (is (nil? (f)))
    (is (= 2 @calls))))

(deftest reference-options-test
  (doseq [options [{:weak-keys true} {:weak-values true} {:soft-values true}]]
    (let [cache (c/build-cache options)
          key (Object.)
          value (Object.)]
      (c/put cache key value)
      (is (identical? value (c/lookup cache key))))))

(deftest conditional-expiration-test
  (let [{:keys [ticker advance!]} (test-clock)
        calls (atom 0)
        f (cached (with-meta (fn [] (swap! calls inc))
                    {:expire 3 :ticker ticker :when pos?}))]
    (is (= 1 (f)))
    (advance! 2)
    (is (= 1 (f)))
    (advance! 1)
    (is (= 2 (f)))))
