(ns coral.core-test
  (:require [clojure.test :refer :all]
            [coral.core :refer :all])
  (:import (java.util UUID)
           (java.util.concurrent Executors TimeUnit)))

(deftest id-correlator-tests
  (testing "`ttl-id-correlator` happy-path"
    (let [correlator (ttl-id-correlator)
          exec (Executors/newSingleThreadScheduledExecutor)
          fut (.scheduleAtFixedRate exec
                ^Runnable (bound-fn []
                            (let [id (str (UUID/randomUUID))
                                  process-time (rand-int 1000)
                                  p (id->promise correlator id)]
                              (future
                                (Thread/sleep process-time)
                                (deliver-for-id correlator id process-time)
                                (is (= process-time @p)))))
                0 500 TimeUnit/MILLISECONDS)]
      (Thread/sleep 20000) ;; allows for 39 assertions
      (future-cancel fut)
      (is (empty? (clear-all! correlator)))
      (.shutdown exec)
      )
    )

  (testing "`ttl-id-correlator` sad-path"
    (let [correlator (ttl-id-correlator {:a (promise)} 5000)]
      (is (thrown? IllegalStateException (id->promise correlator :a))) ;; exists already
      (is (thrown? IllegalStateException (deliver-for-id correlator :b 2))) ;; not found
      )
    )

  )
