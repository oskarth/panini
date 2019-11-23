(ns ^:figwheel-hooks panini.core
  (:require
   [goog.dom :as gdom]
   [rum.core :as rum]
   [instaparse.core :as insta]))

;; TODO: Better example
;; TODO: Deploy to GH pages
;; TODO: Fix README

;; Instaparse

(def grammar
  "S = AB*
     AB = A B
     A = 'a'+
     B = 'b'+")

(def grammar-day
  "day = 'mon' / 'tue' / 'wed' / 'thu' / 'fri' / 'sat' / 'sun'")

(def as-and-bs (insta/parser grammar))

(def weekday-parser
  (insta/parser grammar-day :input-format :abnf))

;; Rum and Figwheel

(println "This text is printed from src/panini/core.cljs. Go ahead and edit it and see reloading in action.")

(defn multiply [a b] (* a b))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Panini - ABNF editor"
                          :input ""
                          :grammar grammar-day
                          :grammar-error ""
                          ;; this is derived data
                          :parser weekday-parser}))

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

(defn parse-or-error [{:keys [parser input]}]
  (if (insta/failure? (parser input))
    (pr-str (parser input))
    (with-out-str (cljs.pprint/pprint (parser input)))))

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
                   :id        "insta-input"
                   :class     ["input_active" "input_error"]
                   :style     {:background-color "#EEE"
                               :width 600
                               :height 400}
                   :on-change (fn [e]
                                (try-parse-input! (.. e -target -value)))}]
       #_[:h3 "Result"]
       [:pre (parse-or-error (rum/react app-state))]]
      [:div#right
       [:h3 "Grammar"]
       [:textarea {:type      "text"
                   :value     (:grammar (rum/react app-state))
                   :allow-full-screen true
                   :id        "insta-grammar"
                   :class     ["input_active" "input_error"]
                   :style     {:background-color "#EEE"
                               :width 600
                               :height 400}
                   :on-change (fn [e]
                                (try-parse-grammar! (.. e -target -value)))}]
       [:pre (:grammar-error (rum/react app-state))]]
      ]]))

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

