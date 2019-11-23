(ns ^:figwheel-hooks panini.core
  (:require
   [goog.dom :as gdom]
   [rum.core :as rum]
   [instaparse.core :as insta]))

;; Instaparse

(def grammar
  "S = AB*
     AB = A B
     A = 'a'+
     B = 'b'+")

(def as-and-bs (insta/parser grammar))

;; Rum and Figwheel

(println "This text is printed from src/panini/core.cljs. Go ahead and edit it and see reloading in action.")

(defn multiply [a b] (* a b))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Panini - ABNF editor"}))

(defn get-app-element []
  (gdom/getElement "app"))

(rum/defc hello-world []
  (let [input "aaaaabbbaaaabb"]
    [:div
     [:h1 (:text @app-state)]
     [:h3 "Instaparse grammar"]
     [:p (pr-str as-and-bs)]
     [:h3 "Instaparse input"]
     [:p (str input)]
     [:h3 "Instaparse result"]
     [:p (str (as-and-bs input))]]))

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

