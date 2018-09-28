(ns aerial.saite.examples

  (:require [clojure.string :as cljstr]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]

            [aerial.fs :as fs]
            [aerial.utils.string :as str]
            [aerial.utils.io :refer [letio] :as io]
            [aerial.utils.coll :refer [vfold] :as coll]
            [aerial.utils.math :as m]
            [aerial.utils.math.probs-stats :as p]
            [aerial.utils.math.infoth :as it]

            [aerial.bio.utils.files :as bufiles]
            [aerial.bio.utils.aligners :as aln]

            [aerial.hanami.common :as hc :refer [RMV]]
            [aerial.hanami.templates :as ht]
            [aerial.hanami.core :as hmi]

            [aerial.saite.common :as ac]
            [aerial.saite.templates :as at]))


;;; Background colors
;;;
;;; :BACKGROUND "beige"
;;; :BACKGROUND "aliceblue"
;;; :BACKGROUND "floralwhite" ; <-- These are
;;; :BACKGROUND "ivory"       ; <-- the better
;;; :BACKGROUND "mintcream"
;;; :BACKGROUND "oldlace"


;;; Simple scatter with template
(->> (hc/xform ht/point-chart
       :HEIGHT 300 :WIDTH 400
       ;;:DATA (->> "http://localhost:3003/data/cars.json" slurp json/read-str)
       :UDATA "data/cars.json"
       :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
     hmi/sv!)

(->>
 (hc/xform
  {:usermeta :USERDATA
   :data {:url "data/cars.json"},
   :mark "point",
   :encoding {:x {:field "Horsepower", :type "quantitative"},
              :y {:field "Miles_per_Gallon", :type "quantitative"},
              :color {:field "Origin", :type "nominal"}}})
 hmi/sv!)


;;; Simple Barchart with instrumented template
(->>
 (let [data [{:a "A", :b 28 },
             {:a "B", :b 55 },
             {:a "C", :b 43 },
             {:a "D", :b 91 },
             {:a "E", :b 81 },
             {:a "F", :b 53 },
             {:a "G", :b 19 },
             {:a "H", :b 87 },
             {:a "I", :b 52 }]
       min -10.0
       minstr (-> min str (cljstr/split #"\.") first)
       max 10.0
       maxstr (-> max str (cljstr/split #"\.") first (#(str "+" %)))]
   (hc/xform ht/bar-chart
             :USERDATA
             (merge
              (hc/get-default :USERDATA)
              {:vid :bc1
               :slider `[[gap :size "10px"] [label :label "Add Bar"]
                         [label :label ~minstr]
                         [slider
                          :model :m1
                          :min ~min, :max ~max, :step 1.0
                          :width "200px"
                          :on-change :oc1]
                         [label :label ~maxstr]
                         [input-text
                          :model :m1
                          :width "60px", :height "26px"
                          :on-change :oc2]]})
             :HEIGHT 300, :WIDTH 350
             :X "a" :XTYPE "ordinal" :XTITLE "Foo" :Y "b" :YTITLE "Bar"
             :DATA data))
 hmi/sv!)

(hmi/sd! {:usermeta {:msgop :data :vid :bc1}
          :data (mapv #(assoc % :b (+ 50 (% :b)))
                      [{:a "A", :b 28 },{:a "B", :b 55 },{:a "C", :b 43 },
                       {:a "D", :b 91 },{:a "E", :b 81 },{:a "F", :b 53 },
                       {:a "G", :b 19 },{:a "H", :b 87 },{:a "I", :b 52 }])})


;;; Overview + Detail
(->>
 (hc/xform
  {:usermeta :USERDATA
   :data {:url "data/sp500.csv"},
   :vconcat
   [{:width 480,
     :mark "area",
     :encoding
     {:x
      {:field "date",
       :type "temporal",
       :scale {:domain {:selection "brush"}},
       :axis {:title ""}},
      :y {:field "price", :type "quantitative"}}}
    {:width 480,
     :height 60,
     :mark "area",
     :selection {:brush {:type "interval", :encodings ["x"]}},
     :encoding
     {:x {:field "date", :type "temporal"},
      :y
      {:field "price",
       :type "quantitative",
       :axis {:tickCount 3, :grid false}}}}]})
 hmi/sv!)


;;; Area Chart
(-> (hc/xform
     ht/layer-chart
     :UDATA  "data/unemployment-across-industries.json"
     :LAYER [(hc/xform ht/area-layer :TOOLTIP RMV
               :X :date, :XTYPE :temporal, :XUNIT :yearmonth, :XFORMAT "%Y"
               :Y "count" :AGG "sum"
               :COLOR {:field "series", :type "nominal", :scale {:scheme "category20b"}})])
    hmi/sv!)



;;; Log scales, error bars
(->>
 (hc/xform
  {:usermeta :USERDATA
   :height 600,
   :width 600,
   :data
   {:values
    [{:dose 0.5, :response 32659}
     {:dose 0.5, :response 40659}
     {:dose 0.5, :response 29000}
     {:dose 1, :response 31781}
     {:dose 1, :response 30781}
     {:dose 1, :response 35781}
     {:dose 2, :response 30054}
     {:dose 4, :response 29398}
     {:dose 5, :response 27779}
     {:dose 10, :response 27915}
     {:dose 15, :response 27410}
     {:dose 20, :response 25819}
     {:dose 50, :response 23999}
     {:dose 50, :response 25999}
     {:dose 50, :response 20999}]},
   :layer
   [{:selection
     {:grid
      {:type "interval",
       :bind "scales",
       :on "[mousedown, window:mouseup] > window:mousemove!",
       :encodings ["x" "y"],
       :translate "[mousedown, window:mouseup] > window:mousemove!",
       :zoom "wheel!",
       :mark {:fill "#333", :fillOpacity 0.125, :stroke "white"},
       :resolve "global"}},
     :mark {:type "point", :filled true, :color "green"},
     :encoding
     {:x {:field "dose", :type "quantitative", :scale {:type "log"}},
      :y {:field "response", :type "quantitative", :aggregate "mean"}}}
    #_{:mark {:type "errorbar", :ticks true},
       :encoding
       {:x {:field "dose", :type "quantitative", :scale {:zero false}},
        :y {:field "response", :type "quantitative"},
        :color {:value "#4682b4"}}}]})
 hmi/sv!)


(->>
 (hc/xform
  {:usermeta :USERDATA
   :height 600,
   :width 600,
   :data
   {:values
    [{:Dose 0.5, :Response 32659.00003}
     {:Dose 0.5, :Response 40659.00002340234}
     {:Dose 0.5, :Response 29000}
     {:Dose 1, :Response 31781}
     {:Dose 1, :Response 30781}
     {:Dose 1, :Response 35781}
     {:Dose 2, :Response 30054}
     {:Dose 4, :Response 29398}
     {:drc_dose 0.05, :drc_ll3 35597.08053881955}
     {:drc_dose 1, :drc_ll3 35597.08053881955}
     {:drc_dose 2, :drc_ll3 35597.08053881955}
     {:drc_dose 5, :drc_ll3 35597.08053881955}
     {:drc_dose 10, :drc_ll3 35597.08053881955}
     {:drc_dose 50, :drc_ll3 35597.08053881955}
     {:drc_dose 200, :drc_ll3 35597.08053881955}
     {:drc_dose 1000, :drc_ll3 35597.08053881955}]},
   :layer
   [{:selection {:view {:type "interval", :bind "scales"}},
     :mark {:type "point", :filled true, :color "black"},
     :encoding
     {:x {:field "Dose", :type "quantitative", :scale {:type "log"}},
      :y {:field "Response", :type "quantitative", :aggregate "mean"}}}
    #_{:mark {:type "errorbar", :ticks true},
       :encoding
       {:x {:field "Dose", :type "quantitative", :scale {:zero false}},
        :y {:field "Response", :type "quantitative"},
        :color {:value "black"}}}
    {:mark {:type "line", :color "red"},
     :encoding
     {:x {:field "drc_dose", :type "quantitative"},
      :y {:field "drc_ll3", :type "quantitative"}}}]})
 hmi/sv!)




;;; Geo Example
(->>
 (hc/xform
  {:usermeta :USERDATA
   :width 700,
   :height 500,
   :data {:url "data/airports.csv"},
   :projection {:type "albersUsa"},
   :mark "circle",
   :encoding {:longitude {:field "longitude", :type "quantitative"},
              :latitude {:field "latitude", :type "quantitative"},
              :tooltip [{:field "name", :type "nominal"}
                        {:field "longitude", :type "quantitative"}
                        {:field "latitude", :type "quantitative"}],
              :size {:value 10}},
   :config {:view {:stroke "transparent"}}}
  :TID :geo)
 hmi/sv!)


;;; Multi Chart - cols and rows
;;;
(->>
 [(let [data (->> (range 0.005 0.999 0.001)
                  (mapv (fn[p] {:x p, :y (- (m/log2 p)) :col "SI"})))]
    ;; Self Info - unexpectedness
    (hc/xform ht/layer-chart
              :TID :multi ;:TOPTS {:order :col, :size "none"}
              :TITLE "Self Information (unexpectedness)"
              :LAYER [(hc/xform ht/line-layer
                                :XTITLE "Probability of event"
                                :YTITLE "-log(p)")
                      (hc/xform ht/xrule-layer :AGG "mean")]
              :DATA data))
  ;; Entropy - unpredictability
  (let [data (->> (range 0.00005 0.9999 0.001)
                  (mapv (fn[p] {:x p,
                               :y (- (- (* p (m/log2 p)))
                                     (* (- 1 p) (m/log2 (- 1 p))))})))]
    (hc/xform ht/layer-chart
              :TID :multi
              :TITLE "Entropy (Unpredictability)"
              :LAYER [(hc/xform ht/gen-encode-layer
                                :MARK "line"
                                :XTITLE "Probability of event" :YTITLE "H(p)")
                      (hc/xform ht/xrule-layer :AGG "mean")]
              :DATA data))]
 hmi/sv!)



;;; Some distributions
;;;;
(def obsdist
  (let [obs [[0 9] [1 78] [2 305] [3 752] [4 1150] [5 1166]
             [6 899] [7 460] [8 644] [9 533] [10 504]]
        totcnt (->> obs (mapv second) (apply +))
        pdist (map (fn[[k cnt]] [k (double (/ cnt totcnt))]) obs)]
    pdist))
;;(p/mean obsdist) => 5.7
(->>
 [(hc/xform ht/layer-chart
            :TID :dists :TOPTS {:order :row, :size "auto"}
            :TITLE "A Real (obvserved) distribution with incorrect sample mean"
            :LAYER
            [(hc/xform ht/bar-layer :XTITLE "Count" :YTITLE "Probability")
             (hc/xform ht/xrule-layer :AGG "mean")]
            :DATA (mapv (fn[[x y]] {:x x :y y :m 5.7}) obsdist))

  (hc/xform ht/layer-chart
            :TID :dists
            :TITLE "The same distribution with correct weighted mean"
            :LAYER
            [(hc/xform ht/bar-layer :XTITLE "Count" :YTITLE "Probability")
             (hc/xform ht/xrule-layer :X "m")]
            :DATA (mapv (fn[[x y]] {:x x :y y :m 5.7}) obsdist))]
 hmi/sv!)



;;; Contour maps (a Vega template!)
;;;
(->>
 (hc/xform
  ht/contour-plot
  :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
  :HEIGHT 400, :WIDTH 500
  :X "Horsepower", :XTITLE "Engine Horsepower"
  :Y "Miles_per_Gallon" :YTITLE "Miles/Gallon"
  :UDATA "data/cars.json"
  :XFORM-EXPR #(let [d1 (% :X)
                     d2 (% :Y)]
                 (format "datum['%s'] != null && datum['%s'] !=null" d1 d2)))
 hmi/sv!)

(->>
 (hc/xform
  ht/contour-plot
  :HEIGHT 500, :WIDTH 600
  :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
  :DATA (take 400 (repeatedly #(do {:x (rand-int 300) :y (rand-int 50)})))
  :XFORM-EXPR #(let [d1 (% :X)
                     d2 (% :Y)]
                 (format "datum['%s'] != null && datum['%s'] !=null" d1 d2)))
 hmi/sv!)



;;; ENTROPY GRAPHS ---------------------------------------------------

;;; Self Info - unexpectedness
(->>
 (let [data (->> (range 0.005 0.999 0.001)
                 (mapv (fn[p] {:x p, :y (- (m/log2 p)) :col "SI"})))]
   (hc/xform ht/layer-chart
             :TITLE "Self Information (unexpectedness)"
             :LAYER [(hc/xform ht/xrule-layer :AGG "mean")
                     (hc/xform ht/line-layer
                               :XTITLE "Probability of event"
                               :YTITLE "-log(p)")]
             :DATA data))
 hmi/sv!)


;;; Entropy - unpredictability
(->>
 (let [data (->> (range 0.00005 0.9999 0.001)
                 (mapv (fn[p] {:x p,
                              :y (- (- (* p (m/log2 p)))
                                    (* (- 1 p) (m/log2 (- 1 p))))})))]
   (hc/xform ht/layer-chart
             :TITLE "Entropy (Unpredictability)"
             :LAYER [(hc/xform ht/gen-encode-layer
                               :MARK "line"
                               :XTITLE "Probability of event" :YTITLE "H(p)")
                     (hc/xform ht/xrule-layer :AGG "mean")]
             :DATA data))
 hmi/sv!)


;;; KLD real vs binomial
(->>
 (let [data (mapv #(let [RE (it/KLD (->> obsdist (into {}))
                                    (->> (p/binomial-dist 10 %)
                                         (into {})))
                         REtt (ac/roundit RE)
                         ptt (ac/roundit % :places 2)]
                     {:x % :y RE})
                  (range 0.06 0.98 0.01))]
   (hc/xform ht/line-chart
             :POINT true
             :TITLE "KLD minimum entropy: True P to Binomial Q estimate"
             :XTITLE "Binomial Distribution P paramter"
             :YTITLE "KLD(P||Q)"
             :DATA data))
 hmi/sv!)


;; JSD real vs binomial
(->>
 (let [data (mapv #(let [RE (it/jensen-shannon
                             (->> obsdist (into {}))
                             (->> (p/binomial-dist 10 %)
                                  (into {})))
                         REtt (ac/roundit RE)
                         ptt (ac/roundit % :places 2)]
                     {:x % :y RE})
                  (range 0.06 0.98 0.01))]
   (hc/xform ht/line-chart
             :POINT true
             :TITLE "JSD minimum entropy: True P to Binomial Q estimate"
             :XTITLE "Binomial Distribution P paramter"
             :YTITLE "JSD(P||Q)"
             :DATA data))
 hmi/sv!)
;;; Sqrt(JSD) real vs binomial
(->>
 (let [data (mapv #(let [RE (Math/sqrt
                             (it/jensen-shannon
                              (->> obsdist (into {}))
                              (->> (p/binomial-dist 10 %)
                                   (into {}))))
                         REtt (ac/roundit RE)
                         ptt (ac/roundit % :places 2)]
                     {:x % :y RE})
                  (range 0.06 0.98 0.01))]
   (hc/xform ht/line-chart
             :POINT true
             :TITLE "JSD minimum entropy: True P to Binomial Q estimate"
             :XTITLE "Binomial Distribution P paramter"
             :YTITLE "JSD(P||Q)"
             :DATA data))
 hmi/sv!)


(->>
 (let [JSD it/jensen-shannon #_(comp #(Math/sqrt (double %)) it/jensen-shannon)
       data (concat
             (mapv #(let [RE (it/KLD (->> obsdist (into {}))
                                     (->> (p/binomial-dist 10 %)
                                          (into {})))]
                      {:x % :y RE :RE "KLD"})
                   (range 0.06 0.98 0.01))
             (mapv #(let [RE (JSD
                              (->> obsdist (into {}))
                              (->> (p/binomial-dist 10 %)
                                   (into {})))]
                      {:x % :y RE :RE "JSD"})
                   (range 0.06 0.98 0.01)))]
   (hc/xform ht/layer-chart
     :TITLE "Minimum entropy: True P to Binomial Q estimate"
     :DATA data
     :LAYER [(hc/xform ht/line-chart
                       :POINT true
                       :TRANSFORM [{:filter {:field "RE" :equal "KLD"}}]
                       :SELECTION ht/interval-scales :INAME "grid1"
                       :COLOR {:field "RE" :type "nominal"
                               :legend {:type "symbol" :offset 0 :title "RE"}}
                       :XTITLE "Binomial Distribution P paramter"
                       :YTITLE "KLD(P||Q)")
             (hc/xform ht/line-chart
                       :POINT true
                       :TRANSFORM [{:filter {:field "RE" :equal "JSD"}}]
                       :SELECTION ht/interval-scales :INAME "grid2"
                       :COLOR {:field "RE" :type "nominal"
                               :legend {:type "symbol" :offset 0 :title "RE"}}
                       :XTITLE "Binomial Distribution P paramter"
                       :YTITLE "JSD(P||Q)")]
     :RESOLVE {:scale {"y" "independent"}}))
 hmi/sv!)

;;; END ENTROPY GRAPHS ---------------------------------------------------






;;; 'Real'/observed distributions vs binomial models
;;;

(->>
 (let [data (concat (->> obsdist
                         (mapv (fn[[x y]]
                                 {:cnt x :y y :dist "Real"})))
                    (->> (p/binomial-dist 10 0.57)
                         (mapv (fn[[x y]]
                                 {:cnt x :y (ac/roundit y)
                                  :dist "Binomial"}))))]
   (hc/xform ht/grouped-bar-chart
     :TID :dists
     :TITLE "Real vs Binomial 0.57", :TOFFSET 40
     :WIDTH (-> 550 (/ 11) double Math/round (- 15))
     :DATA data
     :SELECTION (hc/xform ht/interval-scales :INAME "grid" :ENCODINGS ["y"])
     :X "dist" :XTYPE "nominal" :XTITLE ""
     :Y "y" :YTITLE "Probability"
     :COLOR ht/default-color
     :COLUMN "cnt" :COLTYPE "ordinal"))
 hmi/sv!)


(->>
 (let [data (concat (->> obsdist
                         (mapv (fn[[x y]]
                                 {:cnt x :y y :dist "Real"
                                  :tt (str y)})))
                    (mapcat #(let [l (ac/roundit %)]
                               (->> (p/binomial-dist 10 %)
                                    (mapv (fn[[x y]]
                                            {:cnt x :y (ac/roundit y)
                                             :dist (str l)}))))
                            (range 0.1 0.9 0.2)))]
   (hc/xform ht/grouped-bar-chart
             :TID :dists
             :TITLE "Real distribution vs Binomials", :TOFFSET 10
             :HEIGHT 80
             :DATA data
             :X "cnt" :XTYPE "ordinal" :XTITLE ""
             :Y "y" :YTITLE "Probability"
             :COLOR ht/default-color :CFIELD :ROW :CTYPE :ROWTYPE
             :ROW "dist" :ROWTYPE "nominal"))
 hmi/sv!)


(->>
 (let [data (concat (->> obsdist
                         (mapv (fn[[x y]]
                                 {:cnt x :y y :dist "Real"
                                  :tt (str y)})))
                    (mapcat #(let [l (ac/roundit %)]
                               (->> (p/binomial-dist 10 %)
                                    (mapv (fn[[x y]]
                                            {:cnt x :y (ac/roundit y)
                                             :dist (str l)}))))
                            (range 0.1 0.9 0.2)))]
   (hc/xform ht/grouped-bar-chart
             :TID :dists
             :TITLE "Real distribution vs Binomials", :TOFFSET 40
             :WIDTH (-> 550 (/ 6) double Math/round (- 15))
             :DATA data
             :X "dist" :XTYPE "nominal" :XTITLE ""
             :Y "y" :YTITLE "Probability"
             :COLOR ht/default-color
             :COLUMN "cnt" :COLTYPE "ordinal"))
 hmi/sv!)




;;;;=====================================================================;;;;




(count panclus.clustering/ntsq)
(def credata
  (let [mx 1.0
        %same (range mx 0.05 -0.05)
        sqs (ac/mutate panclus.clustering/ntsq %same)]
    (->> (ac/opt-wz (concat [["s1" panclus.clustering/ntsq]]
                            (map #(vector (str "s" (+ 2 %1)) %2)
                                 (range) (->> sqs shuffle (take 5))))
                    :alpha "AUGC" :limit 14)
         second (apply concat)
         (reduce (fn[M v] (assoc M (first v) v)) {})
         vals
         (map (fn[[x y _ sq]] {:x x :y y :m 9.0})))))

(->>
 (hc/xform ht/layer-chart
           :TITLE "CRE / Optimal Word Size"
           :DATA (conj credata {:x 1 :y 1.1 :m 9.0} {:x 2 :y 1.63 :m 9.0})
           :LAYER
           [(hc/xform ht/line-layer :XTITLE "Word Size" :YTITLE "CRE")
            (hc/xform ht/xrule-layer :X "m")])
 (hmi/svgl! "Exploring"))





