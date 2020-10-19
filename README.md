# coral

## What
A tiny Clojure library for (in-process) correlation of IDs 
(e.g. request/response scenarios in distributed systems).
Started out as a Kafka helper, but quickly realised there is nothing 
Kafka-specific here (i.e. the system is oblivious to potential taps/sinks). 

The core object (`coral.core.Correlator`) is essentially a custom wrapper around 
`core.cache` impls (wrapped in `atom`), and therefore can be considered stateful. 

## Where
;; TODO

## Usage
First things first 
```clj
;; the only namespace you will probably need
(require '[coral.core :as cc]) 
```

### Create the correlator
This is going to be a somewhat global object. Both your producer(s) and 
consumer(s) will need to have access to it.
```clj
;; TTL-based correlator
(def corr (cc/ttl-id-correlator {} 5000))
```

### id->promise \[correlator id\]
This is the function your local producing logic will call 
(e.g. sending to a `KafkaProducer`). 
```clj
(-> corr
    (cc/id->promise "some-UUID") ;; => a promise 
    (deref 5000 :timeout))
```

### deliver-for-id \[correlator id v\]
This is the function your local consuming logic will call 
(e.g. polling from a `KafkaConsumer`).
```clj
;; this will unblock whoever is waiting on 
;; the promise associated with this id (see above)
(cc/deliver-for-id corr "some-UUID" :whatever) ;; => true
```

## Safety vs Performance
A.K.A. the 'why not use a ConcurrentHashMap' question. The reasons is simple - 
something needs to be done with ids that, for whatever reason, were never 'correlated' 
(i.e. delivered). Think about the typical web-server request/response scenario. 
What should happen in case the response fails to come back? Either something needs to
keep track of those (non-trivial), or some automatic measure should be put in place.
A ttl-cache is simply perfect for this (avoids memory leaks), and fits nicely with the
aforementioned request/response scenario, where you would have a good idea of how long the 
TTL threshold should be (i.e. your own end-to-end latency limits, or you may be in a
 position to know how long your clients are willing/going to wait).

## License

Copyright © 2020 Dimitrios Piliouras

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
