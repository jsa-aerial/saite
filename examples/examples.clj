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

            [aerial.saite.core :as saite]
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


(saite/start 3000)
(saite/stop)


;;; Examples for browser popup renderings. Cut and paste into Clj panel

[ht/point-chart
 :UDATA "data/cars.json"
 :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin"]

[ht/area-chart
 :UDATA  "data/unemployment-across-industries.json"
 :TOOLTIP RMV
 :X :date, :XTYPE :temporal, :XUNIT :yearmonth, :XFORMAT "%Y"
 :Y "count" :AGG "sum"
 :COLOR {:field "series", :type "nominal",
         :scale {:scheme "category20b"}}]

[ht/layer-chart
 :UDATA  "data/unemployment-across-industries.json"
 :LAYER [[ht/area-layer :TOOLTIP RMV
          :X :date, :XTYPE :temporal, :XUNIT :yearmonth, :XFORMAT "%Y"
          :Y "count" :AGG "sum"
          :COLOR {:field "series", :type "nominal",
                  :scale {:scheme "category20b"}}]]]

[ht/tree-layout
 :MODE "vega"
 :UDATA "data/flare.json"
 :WIDTH 650, :HEIGHT 1600
 :LINKSHAPE "diagonal" :LAYOUT "cluster"
 :CFIELD "depth"]





;;; Simple scatter with template
(->> (hc/xform ht/point-chart
       ;;:DATA (->> "http://localhost:3003/data/cars.json" slurp json/read-str)
       :UDATA "data/cars.json"
       :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
     hmi/sv!)




(let [_ (hc/add-defaults
         :FMNM #(-> :SIDE % name cljstr/capitalize)
         :STYLE hc/RMV)
      frame-template {:frame
                      {:SIDE `[[gap :size :GAP]
                               [p {:style :STYLE}
                                "This is the " [:span.bold :FMNM]
                                " 'board' of a picture "
                                [:span.italic.bold "frame."]]]}}
      frame-top (hc/xform
                 frame-template :SIDE :top :GAP "10px")

      frame-left (hc/xform
                  frame-template :SIDE :left :GAP "10px"
                  :STYLE {:width "100px" :min-width "50px"})
      frame-right (merge-with merge
                   (hc/xform
                    frame-template :SIDE :right :GAP "2px"
                    :STYLE {:width "100px" :min-width "50px"})
                   (hc/xform
                    frame-template :SIDE :left :GAP "2px"
                    :STYLE {:width "100px" :min-width "50px"
                            :color "white"}))
      frame-bot (hc/xform
                 frame-template :SIDE :bottom :GAP "10px")]
  (->> (mapv #(hc/xform ht/point-chart
                :HEIGHT 200 :WIDTH 250 :TID :picframes
                :USERDATA (merge (hc/get-default :USERDATA) %)
                :UDATA "data/cars.json"
                :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
             [frame-top frame-left frame-bot frame-right])
       hmi/sv!))


(let [text "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."
      top `[[gap :size "150px"]
            [p "An example showing a "
             [:span.bold "picture "] [:span.italic.bold "frame"]
             ". This is the top 'board'"
             [:br] ~text]]
      left `[[gap :size "10px"]
             [p {:style {:width "100px" :min-width "50px"}}
              "Some text on the " [:span.bold "left:"] [:br] ~text]]
      right `[[gap :size "2px"]
              [p {:style {:width "200px" :min-width "50px"
                          :font-size "20px" :color "red"}}
               "Some large text on the " [:span.bold "right:"] [:br]
               ~(.substring text 0 180)]]
      bottom `[[gap :size "200px"]
               [title :level :level3
                :label [p {:style {:font-size "large"}}
                        "Some text on the "
                        [:span.bold "bottom"] [:br]
                        "With a cool info button "
                        [info-button
                         :position :right-center
                         :info
                         [:p "Check out Saite Visualizer!" [:br]
                          "Built with Hanami!" [:br]
                          [hyperlink-href
                           :label "Saite "
                           :href  "https://github.com/jsa-aerial/saite"
                           :target "_blank"]]]]]]]
  (->> [(hc/xform ht/point-chart
          :TID :picframes
          :TOP top :BOTTOM bottom :LEFT left :RIGHT right
          :UDATA "data/cars.json"
          :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")]
       hmi/sv!))


;;; Empty picture frame
;;;
(let [text "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."
      top `[[gap :size "50px"]
            [p {:style {:width "600px" :min-width "50px"}}
             "An example empty picture frame showing all four areas."
             " This is the " [:span.bold "top"] " area. "
             ~text ~text ~text]]
      left `[[gap :size "50px"]
             [p {:style {:width "300px" :min-width "50px"}}
              "The " [:span.bold "left "] "area as a column of text. "
              ~text ~text ~text ~text]]
      right `[[gap :size "70px"]
              [p {:style {:width "300px" :min-width "50px"}}
               "The " [:span.bold "right "] "area as a column of text. "
               ~text ~text ~text ~text]]
      bottom `[[gap :size "50px"]
               [v-box
                :children
                [[p {:style {:width "600px" :min-width "50px"}}
                  "The " [:span.bold "bottom "]
                  "area showing a variety of text. "
                  [:span.italic ~text] [:span.bold ~text]]
                 [p {:style {:width "400px" :min-width "50px"
                             :font-size "20px"}}
                  "some TeX: " "\\(f(x) = \\sqrt x\\)"]
                 [md {:style {:font-size "16px" :color "blue"}}
                  "#### Some Markup
* **Item 1** Lorem ipsum dolor sit amet, consectetur adipiscing elit.
* **Item 2** Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."]
                 [p {:style {:width "600px" :min-width "50px"
                             :color "red"}}
                  ~text]]]]]
  (->> (hc/xform ht/empty-chart
        :TID :picframes :TOP top :BOTTOM bottom :LEFT left :RIGHT right)
       hmi/sv!))


;;; With and without chart frame
;;;
(let [text "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quod si ita est, sequitur id ipsum, quod te velle video, omnes semper beatos esse sapientes. Tamen a proposito, inquam, aberramus."
      top `[[gap :size "50px"]
            [p "Here's a 'typical' chart/plot filled picture frame."
             "It only has the top area"
             [:br] ~text]]
      left `[[gap :size "20px"]
             [p {:style {:width "200px" :min-width "50px"}}
              "This is an empty frame with a " [:span.bold "left "]
              "column of text" [:br] ~text ~text ~text ~text]]
      right `[[gap :size "30px"]
              [p {:style {:width "200px" :min-width "50px"}}
               "And a " [:span.bold "right "]
               "column of text"
               [:br] ~text ~text ~text ~text]]]
  (->> [(hc/xform ht/point-chart
          :TID :picframes :UDATA "data/cars.json"
          :TOP top
          :X "Horsepower" :Y "Miles_per_Gallon" :COLOR "Origin")
        (hc/xform ht/empty-chart
          :TID :picframes
          :LEFT left :RIGHT right)]
       hmi/sv!))



(->>
 (hc/xform
  {:usermeta :USERDATA
   :data {:url "data/cars.json"},
   :mark "point",
   :encoding {:x {:field "Horsepower", :type "quantitative"},
              :y {:field "Miles_per_Gallon", :type "quantitative"},
              :color {:field "Origin", :type "nominal"}}}
  :VID :scatter-1
  :LEFT `[[gap :size "10px"]
          [cm :id "cm-scatter-1" :vid :VID
           :init
"(hmi/visualize
 (get-vspec :scatter-1)
 (js/document.getElementById \"scatter-1\"))

(hmi/visualize
 (get-vspec :bc1)
 (js/document.getElementById \"scatter-1\"))

(hmi/visualize
 (get-vspec :sqrt)
 (js/document.getElementById \"scatter-1\"))"]])

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
       maxstr (-> max str (cljstr/split #"\.") first (#(str "+" %)))
       bottom `[[gap :size "50px"]
                [v-box
                 :children
                 [[p "some text to test, default 14px"]
                  [p {:style {:font-size "16px"}}
                   "some tex \\(f(x) = x^2\\), 16px"]
                  [p {:style {:font-size "18px"}}
                   "\\(f(x) = \\sqrt x\\), 18px"]
                  [p {:style {:font-size "20px"}}
                   "\\(ax^2 + bx + c = 0\\), 20px"]]]]]
   (hc/xform ht/bar-chart
             :USERDATA
             (merge
              (hc/get-default :USERDATA)
              {:slider `[[gap :size "10px"] [label :label "Add Bar"]
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
             :BOTTOM bottom :VID :bc1
             :X "a" :XTYPE "ordinal" :XTITLE "Foo" :Y "b" :YTITLE "Bar"
             :DATA data))
 hmi/sv!)

(->
 (hc/xform
  ht/bar-chart
  :UDATA "data/seattle-weather.csv" :TOOLTIP RMV
  :X "date" :XTYPE "ordinal" :XUNIT "month"
  :Y "precipitation" :YAGG "mean")
 hmi/sv!)

;;; Data streaming
#_(hmi/sd! {:usermeta {:msgop :data :vid :bc1}
          :data (mapv #(assoc % :b (+ 50 (% :b)))
                      [{:a "A", :b 28 },{:a "B", :b 55 },{:a "C", :b 43 },
                       {:a "D", :b 91 },{:a "E", :b 81 },{:a "F", :b 53 },
                       {:a "G", :b 19 },{:a "H", :b 87 },{:a "I", :b 52 }])})


(let [data (->> (range 0.001 100.0 0.1)
                (mapv #(do {:x (ac/roundit %)
                            :y (-> % Math/sqrt ac/roundit)})))]
  (->> (hc/xform ht/line-chart :VID :sqrt
        :BOTTOM `[[gap :size "230px"]
                  [p {:style {:font-size "18px"}}
                   "\\(f(x) = \\sqrt x\\)"]]
        :RIGHT
        `[[gap :size "10px"]
          [v-box
           :children
           [(md "#### The square root function")
            (md "* \\(f(x) = \\sqrt x\\)")
            (md "* _two_\n* **three**")]]]
        :DATA data) hmi/sv!))




(md "* P(x) = \\(\\frac{1}{\\sqrt{2\\pi \\sigma^2}} e^{- \\frac{(x - \\mu)^2}{2\\sigma ^2}}\\)")




(hc/get-default :NAME)
(hc/update-defaults :NAME :name)
;;; Tree Layouts
;;;
(->>
 [(hc/xform
   ht/tree-layout
   :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
   :WIDTH 650, :HEIGHT 1600
   :UDATA "data/flare.json"
   :LINKSHAPE "diagonal" :LAYOUT "tidy" :FONTSIZE 11
   :CFIELD "depth")
  (hc/xform
   ht/tree-layout
   :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
   :WIDTH 650, :HEIGHT 1600
   :UDATA "data/flare.json"
   :LINKSHAPE "orthogonal" :LAYOUT "cluster"
   :CFIELD "depth")
  (hc/xform
   ht/tree-layout
   :OPTS (merge (hc/default-opts :vgl) {:mode "vega"})
   :WIDTH 650, :HEIGHT 1600
   :UDATA "data/flare.json"
   :LINKSHAPE "curve" :LAYOUT "tidy"
   :CFIELD "depth")]
 hmi/sv!)


;;; Overview + Detail as straight VGL JSON
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


;;; (hc/update-defaults :WIDTH 480 :HEIGHT 200)
;;; (hc/update-defaults :HEIGHT 400 :WIDTH 450)
(->>
 (hc/xform
  ht/vconcat-chart
  :UDATA "data/sp500.csv",
  :VCONCAT [(hc/xform ht/area-chart
             :X :date, :XTYPE :temporal, :XSCALE {:domain {:selection :brush}}
             :Y :price)
            (hc/xform ht/area-chart
             :HEIGHT 60,
             :SELECTION {:brush {:type :interval, :encodings [:x]}},
             :X :date :XTYPE :temporal
             :Y :price, :YAXIS {:tickCount 3, :grid false})])
 hmi/sv!)

(mapv #(let [{:keys [year month day]} (% :date)]
         (assoc % :vgldate (str year "-" month "-" day)))
      your-data)
      [{:date {:year 2019, :month 2, :day 12}}
       {:date {:year 2019, :month 2, :day 17}}
       {:date {:year 2019, :month 3, :day 1}}])

;;; Area Chart
(-> (hc/xform
     ht/area-chart
     :UDATA  "data/unemployment-across-industries.json"
     :TOOLTIP RMV
     :X :date, :XTYPE :temporal, :XUNIT :yearmonth, :XFORMAT "%Y"
     :Y "count" :AGG "sum"
     :COLOR {:field "series", :type "nominal",
             :scale {:scheme "category20b"}})
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
     {:grid {:type "interval", :bind "scales"}},
     :mark {:type "point", :filled true, :color "green"},
     :encoding
     {:x {:field "dose", :type "quantitative", :scale {:type "log"}},
      :y {:field "response", :type "quantitative", :aggregate "mean"}}}
    {:mark {:type "errorbar", :ticks true},
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
    [{:Dose 0.5, :Response 32659.00003,
      :drc_dose 0.05, :drc_ll3 35597.08053881955},
     {:Dose 0.5, :Response 40659.00002340234,
      :drc_dose 1, :drc_ll3 35597.08053881955},
     {:Dose 0.5, :Response 29000,
      :drc_dose 2, :drc_ll3 35597.08053881955},
     {:Dose 1, :Response 31781,
      :drc_dose 5, :drc_ll3 35597.08053881955},
     {:Dose 1, :Response 30781,
      :drc_dose 10, :drc_ll3 35597.08053881955},
     {:Dose 1, :Response 35781,
      :drc_dose 50, :drc_ll3 35597.08053881955},
     {:Dose 2, :Response 30054,
      :drc_dose 200, :drc_ll3 35597.08053881955},
     {:Dose 4, :Response 29398,
      :drc_dose 1000, :drc_ll3 35597.08053881955}]},
   :layer
   [{:selection {:grid {:type "interval", :bind "scales"}},
     :mark {:type "point", :filled true, :color "black"},
     :encoding
     {:x {:field "Dose", :type "quantitative", :scale {:type "log"}},
      :y {:field "Response", :type "quantitative", :aggregate "mean"}}}
    {:mark {:type "errorbar", :ticks true},
       :encoding
       {:x {:field "Dose", :type "quantitative", :scale {:zero false}},
        :y {:field "Response", :type "quantitative"},
        :color {:value "black"}}}
    {:mark {:type "line", :color "red"},
     :encoding
     {:x {:field "drc_dose", :type "quantitative"},
      :y {:field "drc_ll3", :type "quantitative"}}}]})
 hmi/sv!)




;;; Geo Examples
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

(->>
 (hc/xform
  {:usermeta :USERDATA
   :$schema "https://vega.github.io/schema/vega-lite/v3.json",
   :width 1000,
   :height 600,
   :data
   {:url "https://raw.githubusercontent.com/vega/vega-datasets/gh-pages/data/us-10m.json",
    :format {:type "topojson", :feature "counties"}},
   :transform
   [{:lookup "id",
     :from
     {:data {:url "https://raw.githubusercontent.com/vega/vega-datasets/gh-pages/data/unemployment.tsv"},
      :key "id",
      :fields ["rate"]}}],
   :projection {:type "albersUsa"},
   :mark "geoshape",
   :encoding {:color {:field "rate", :type "quantitative"}}}
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
  :MODE "vega"
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
  :MODE "vega"
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
 [(let [data (mapv #(let [RE (it/jensen-shannon
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
;;; Sqrt(JSD) real vs binomial
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
              :TITLE "SQRT(JSD) minimum entropy: True P to Binomial Q estimate"
              :XTITLE "Binomial Distribution P paramter"
              :YTITLE "JSD(P||Q)"
              :DATA data))]
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





