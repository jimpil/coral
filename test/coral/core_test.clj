(ns coral.core-test
  (:require [clojure.test :refer :all]
            [coral.core :refer :all])
  (:import (java.util UUID)))

(deftest id-correlator-tests
  (testing "`ttl-id-correlator` happy-path"
    (let [correlator (ttl-id-correlator)
          ids (repeatedly 1000 #(str (UUID/randomUUID)))
          promises (mapv (partial id->promise correlator) ids)]
      (future (doall (map (partial deliver-for-id correlator) ids (range))))
      (is (every? int? (map deref promises)))
      (is (empty? (clear-all! correlator)))
      )
    )

  (testing "`ttl-id-correlator` sad-path"
    (let [correlator (ttl-id-correlator {:a (promise)} 5000)]
      (is (thrown? IllegalStateException (id->promise correlator :a))) ;; exists already
      (is (thrown? IllegalStateException (deliver-for-id correlator :b 2))) ;; not found
      )
    )

  )
