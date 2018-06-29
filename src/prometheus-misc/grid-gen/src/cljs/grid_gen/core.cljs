(ns grid-gen.core
  (:require [reagent.core :as reagent]
            [grid-gen.coords :as coords]
            [grid-gen.layout-2 :as layout-2]
            [cljsjs.clipboard]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce app-state
  (reagent/atom {:pixel-pm 5}))

;; coordinate pixels per meter.
(def scale 60)

(defn coords
  [{:keys [start end] :as spar}]
  (let [[sx sy] start
        [ex ey] end
        dx      (- ex sx)
        dy      (- ey sy)
        n       (Math/ceil (/ (Math/sqrt (+ (* dx dx)
                                            (* dy dy)))
                              (/ scale (:pixel-pm @app-state))))
        pitch   (/ 1 n)]
    (vec (map
          (fn [idx]
            [(+ sx  (* idx dx pitch))
             (+ sy  (* idx dy pitch))])
          (range (inc n))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn total-pixels
  [spars]
  (reduce +
          (map (fn [spar] (count (coords spar)))
               spars)))

(defn transform-to-unit
  [x y r max-x max-y]
  [(- (* (/ x max-x) 2 r) r)
   (- (* (/ y max-y) 2 r) r)])

(defn lines->edn
  [spars]
  (->> spars
       (map
        (fn [spar]
          (map
           (fn [[x y]]
             (let [[tx ty] (transform-to-unit x y 10 1000 1000)]
               {:point [tx ty 0]}))
           (coords spar))))
       (reduce concat)
       vec))

(defn edn->pstring
  [edn]
  (.stringify js/JSON (clj->js edn) nil 2))

(defn clipboard-button [label text]
  (let [clipboard-atom (atom nil)]
    (reagent/create-class
     {:display-name "clipboard-button"
      :component-did-mount
      #(let [clipboard (new js/Clipboard (reagent/dom-node %))]
         (reset! clipboard-atom clipboard))
      :component-will-unmount
      #(when-not (nil? @clipboard-atom)
         (.destroy @clipboard-atom)
         (reset! clipboard-atom nil))
      :reagent-render
      (fn []
        [:button.pure-button.clipboard
         {:data-clipboard-text text}
         label])})))

(defn layout-1 [ratom]
  [:div
   [:div
    [:div {:style {:position         "absolute"
                   :z-index          -1
                   :width            600
                   :height           600
                   :background-image "url(template.jpeg)"
                   :background-size  570
                   :filter           "brightness(20%)"}}]
   (into
    [:svg
     {:width   600
      :viewBox "0 0 1000 1000"}]
    (for [spar coords/spars]
      (let [coord (coords spar)]
        ^{:key (str (rand))}
        (into [:g]
              (concat
               (map (fn [[x y]]
                      [:circle {:cx   x :cy y
                                :r    3
                                :fill :purple}])
                    coord)
               (let [[x y] (:end spar)]
                 [[:circle {:cx x :cy y :r 4 :fill :green}]
                  [:text {:x (+ x 30) :y y :fill "yellow"}
                   (count coord)]]))))))]
   [:h3 "Total leds: " (total-pixels coords/spars)]
   [:h4 "Total spars: " (count coords/spars)]
   [:h3 "LED/m: " (:pixel-pm @app-state)]
   [:div
    [:input {:type      "range"
             :min       1.0
             :max       10
             :step      0.5
             :value     (:pixel-pm @app-state)
             :on-change #(swap! app-state assoc :pixel-pm
                                (-> % .-target .-value))}]]
   [clipboard-button "Copy JSON" (edn->pstring (lines->edn coords/spars))]
   [:pre (edn->pstring (lines->edn coords/spars))]])


(defn page [ratom]
  [:div
   [:div
    [:button.pure-button {:on-click #(swap! ratom assoc :page 1)} "1"]
    [:button.pure-button {:on-click #(swap! ratom assoc :page 2)} "2"]]
   (case (:page @ratom)
     1 [layout-1 ratom]
     2 [layout-2/main ratom]
     [layout-2/main ratom])])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (println "dev mode")))

(defn reload []
  (reagent/render [page app-state]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (reload))
