(ns ^:figwheel-hooks panini.core
  (:require
   [cljs.reader :as reader]
   [cljs.pprint :as pprint]
   [goog.dom :as gdom]
   [rum.core :as rum]
   [instaparse.core :as insta]))

(def grammar-day
  "day = 'mon' / 'tue' / 'wed' / 'thu' / 'fri' / 'sat' / 'sun'")

;; 1996-12-19T16:39:57-08:00
(def example-dt "2019-11-23T22:36:50.52Z")

(def zmq-ex-grammar
  "; http://zguide.zeromq.org/py:chapter7#Initial-Design-Cut-the-Protocol
nom-protocol    = open-peering *use-peering

open-peering    = C-OHAI ( S-OHAI-OK / S-WTF )

use-peering     = C-ICANHAZ
                / S-CHEEZBURGER
                / C-HUGZ S-HUGZ-OK
                / S-HUGZ C-HUGZ-OK

; Self-evaluating terminal
C-OHAI = ':C-OHAI'
S-OHAI-OK = ':S-OHAI-OK'
S-WTF = ':S-WTF'
C-ICANHAZ = ':C-ICANHAZ'
S-CHEEZBURGER = ':S-CHEEZBURGER'
C-HUGZ = ':C-HUGZ'
S-HUGZ = ':S-HUGZ'
C-HUGZ-OK = ':C-HUGZ-OK'
S-HUGZ-OK = ':S-HUGZ-OK'
")

(def iso8601-grammar
  "
; ISO8601 - https://tools.ietf.org/rfc/rfc3339.txt
date-time       = full-date 'T' full-time
full-date       = date-fullyear '-' date-month '-' date-mday
date-fullyear   = 4DIGIT
date-month      = 2DIGIT  ; 01-12
date-mday       = 2DIGIT  ; 01-28, 01-29, 01-30, 01-31 based on
; month/year
time-hour       = 2DIGIT  ; 00-23
time-minute     = 2DIGIT  ; 00-59
time-second     = 2DIGIT  ; 00-58, 00-59, 00-60 based on leap second
; rules
time-secfrac    = '.' 1*DIGIT
time-numoffset  = ('+' / '-') time-hour ':' time-minute
time-offset     = 'Z' / time-numoffset

partial-time    = time-hour ':' time-minute ':' time-second
[time-secfrac]
full-time       = partial-time time-offset

; hide field
<DIGIT>       =  %x30-39

  ")

(def iso8601-grammar-hide
  "

; ISO8601 - https://tools.ietf.org/rfc/rfc3339.txt
date-time       = full-date <'T'> full-time
full-date       = date-fullyear <'-'> date-month <'-'> date-mday
date-fullyear   = 4DIGIT
date-month      = 2DIGIT  ; 01-12
date-mday       = 2DIGIT  ; 01-28, 01-29, 01-30, 01-31 based on
; month/year
time-hour       = 2DIGIT  ; 00-23
time-minute     = 2DIGIT  ; 00-59
time-second     = 2DIGIT  ; 00-58, 00-59, 00-60 based on leap second
; rules
time-secfrac    = '.' 1*DIGIT
time-numoffset  = ('+' / '-') time-hour <':'> time-minute
<time-offset>     = 'Z' / time-numoffset

<partial-time>    = time-hour <':'> time-minute <':'> time-second
<[time-secfrac]>
full-time       = partial-time time-offset

<DIGIT>       =  %x30-39
  ")

(def weekday-parser
  (insta/parser grammar-day :input-format :abnf))

(def zmq-ex-input ":C-OHAI:S-OHAI-OK:C-ICANHAZ:S-CHEEZBURGER:C-HUGZ")

(def zmq-ex-parser
  (insta/parser zmq-ex-grammar :input-format :abnf))

(def iso8601-parser
  (insta/parser iso8601-grammar-hide :input-format :abnf))

(defn multiply [a b] (* a b))

(def ex-rules
  {:date-fullyear (comp int str)
   :date-month    (comp int str)
   :date-mday     (comp int str)
   :time-hour     (comp int str)
   :time-minute   (comp int str)
   :time-second   (comp int str)})

;; ok, problem - we need to have a repl here
;; (reader/read-string p/ex-rules-str)
;; returns a list, not actual fn.
;; Alternatively, we can manually figure this out, ie have preludes then merge. Requires a lot of data wrangling though.
(def ex-rules-str
  "{:date-fullyear (comp int str)
   :date-month    (comp int str)
   :date-mday     (comp int str)
   :time-hour     (comp int str)
   :time-minute   (comp int str)
   :time-second   (comp int str)}")

;; reader/read-string ex-rules-str

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Panini - ABNF editor"
                          :grammar-error ""
                          :rules ex-rules-str
                          :transformer {}
                          :input example-dt
                          :grammar iso8601-grammar-hide
                          ;; this is derived data
                          :parser iso8601-parser}))

;;:grammar zmq-ex-grammar
;;:parser zmq-ex-parser}))

;; XXX: This is hacky af
(defn hacky-transform [m]
  (into {}
        (map (fn [[k v]]
               [k (cond (= v '(comp int str)) (comp int str)
                        :else (do
                                (let [err (str "Unable to recognize expression (NYI?): " v)]
                                  (prn err)
                                  (swap! app-state assoc
                                         ;; XXX: Not really grammar error
                                         :grammar-error err)
                                  :xxx)))])
             m)))


;; TODO: Error should be in a more specific "failure-type", not in js/Error e
(defn try-parse-grammar! [grammar]
(try (insta/get-failure (insta/parser grammar :input-format :abnf))
     (swap! app-state assoc
            :parser (insta/parser grammar :input-format :abnf)
            :grammar grammar
            :grammar-error "")
     (catch :default e
       (swap! app-state assoc
              :grammar grammar
              :grammar-error e))))

(defn try-parse-input! [x]
(swap! app-state assoc :input x))

;; XXX: no validation, YOLO
;; TODO: Catch bad forms
(defn try-parse-rules! [rules]
  (let [form (hacky-transform (reader/read-string rules))]
    (prn "FORM:" form #_((:date-fullyear form) "1" "2" "3"))
    (if (map? form)
      (swap! app-state assoc
             :rules rules
             :transformer form)
      (swap! app-state assoc
             :rules rules))))
;; TODO: show errors

(defn parse-or-error [{:keys [parser input transformer]}]
(if (insta/failure? (parser input))
  (pr-str (parser input))
  (with-out-str
    (pprint/pprint
     (insta/transform
      transformer (parser input))))))

(defn get-app-element []
  (gdom/getElement "app"))

(rum/defc hello-world < rum/reactive []
  (let [input "mon"]
    [:div
     [:div
      [:h1 (:text @app-state)]]
     [:div#wrapper
      [:div#left
       [:h3 "Input"]
       [:textarea {:type      "text"
                   :value     (:input (rum/react app-state))
                   :allow-full-screen true
                   :autoFocus  true
                   :spellCheck false
                   :id        "insta-input"
                   :class     ["input_active" "input_error"]
                   :style     {:background-color "#EEE"
                               :width 600
                               :height 300}
                   :on-change (fn [e]
                                (try-parse-input! (.. e -target -value)))}]

       ;; TODO: Rewrite rules here
       [:textarea {:type      "text"
                   :value     (:rules (rum/react app-state))
                   :allow-full-screen true
                   :autoCocus  true
                   :spellCheck false
                   :id        "insta-input-2"
                   :class     ["input_active" "input_error"]
                   :style     {:background-color "#EEE"
                               :width 600
                               :height 100}
                   :on-change (fn [e]
                                (try-parse-rules! (.. e -target -value)))}]

       #_[:h3 "Result"]
       [:pre (parse-or-error (rum/react app-state))]]
      [:div#right
       [:h3 "Grammar"]
       [:textarea {:type      "text"
                   :value     (:grammar (rum/react app-state))
                   :allow-full-screen true
                   :spellCheck false
                   :id        "insta-grammar"
                   :class     ["input_active" "input_error"]
                   :style     {:background-color "#EEE"
                               :width 600
                               :height 400}
                   :on-change (fn [e]
                                (try-parse-grammar! (.. e -target -value)))}]
       [:pre (:grammar-error (rum/react app-state))]]]]))

(defn mount [el]
  (rum/mount (hello-world) el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element))
;; optionally touch your app-state to force rerendering depending on
;; your application
;; (swap! app-state update-in [:__figwheel_counter] inc)

