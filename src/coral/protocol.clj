(ns coral.protocol)

(defprotocol ICorrelator
  "Correlator abstraction.
   - register-id!: Expects an <id>, and returns a promise (or whatever <exists!> returns)
   - deliver-id! : Expects an <id>, and returns true, or whatever <not-found!> returns"
  ;; adds a new promise and returns it (client blocks on it waiting)
  (register-id! [this id exists!])
  ;; finds the promise, delivers `v` to it (unblocks client), and removes it
  (deliver-id!  [this id v not-found!])
  ;; clears everything
  (clear!       [this]))
