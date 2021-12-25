(ns fmnoise.coldbrew-test
  (:require [clojure.test :refer [deftest is]]
            [fmnoise.coldbrew :refer [defcached cached]]))

(deftest cached-fn-test
  (let [counter (atom 0)
        cf (cached (with-meta (fn [a b] (swap! counter inc) (+ a b)) {:expire 3}))
        r1 (cf 1 2)
        c1 @counter
        r2 (cf 1 2)
        c2 @counter
        _ (Thread/sleep 3000)
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
  (let [counter (atom 0)]
    (defcached cached-func [a b]
      ^{:expire 3}
      [a b]
      (swap! counter inc)
      (+ a b))
    (let [r1 (cached-func 1 2)
          c1 @counter
          r2 (cached-func 1 2)
          c2 @counter
          _ (Thread/sleep 3000)
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
