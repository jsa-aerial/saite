[{:overview {:label "Overview", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :overview, :$split 0, :fn [quote interactive-doc-tab], :ns aerial.saite.usercode, :width "730px", :src "\n(hc/xform\n ht/empty-chart :FID :f1\n :TOP '[\n [gap :size \"20px\"]\n [md {:style {:width 900 :font-size \"30px\"}}\n\"\n# Overview\n\n* Hanami\n  * Short intro\n  * Template review with code examples\n  * Tab capability review\n  * Picture frame review\n\n* Saite\n  * Short history\n  * Basic current state\n  * Controls quick look\n  * Editor capabilities\n  * Server side support\n  * Publishing\n  * Format structure for docs(?)\n  * Futures / Road map\n\"]\n])", :out-height "100px", :eid "ed-overview", :height "700px"}}, :specs [{:usermeta {:frame {:top [[gap :size "20px"] [md {:style {:font-size "30px", :width 900}} "\n# Overview\n\n* Hanami\n  * Short intro\n  * Template review with code examples\n  * Tab capability review\n  * Picture frame review\n\n* Saite\n  * Short history\n  * Basic current state\n  * Controls quick look\n  * Editor capabilities\n  * Server side support\n  * Publishing\n  * Format structure for docs(?)\n  * Futures / Road map\n"]], :pos :after, :at :end, :fid :f1}, :session-name "Exploring", :opts {:mode "vega-lite", :source false, :export {:png true, :svg true}, :renderer "canvas", :scaleFactor 1, :editor true}, :msgop :tabs, :tab {:label "Expl1", :id :expl1, :opts {:eltsper 2, :size "auto", :order :row}}}}]}} {:hanami {:label "Hanami", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :hanami, :$split 0, :fn [quote interactive-doc-tab], :ns aerial.saite.usercode, :width "730px", :src "\n(hc/xform\n ht/empty-chart :FID :f1\n :TOP '[\n [gap :size \"20px\"]\n [md {:style {:width 900 :font-size \"25px\"}}\n\"\n# Hanami\n\n* Library \n  * Enables construction of vis apps on top of Vega and Vega-Lite\n    * Analogous to Shiny or Dash\n  * Server/Client, and Client only apps\n  * Template system\n    * Templates parameterized by *substitution keys*\n      * Abstract visualizations\n    * Recursively transformed into legal VG/VGL specs (and other...)\n    * Default set of templates (aerial.hanami.templates)\n    * Default set of substitution keys (aerial.hanami.common)\n    * General transformer `xform`\n    * Completely changeable to suite your wants/needs\n  * Simple potent messaging\n  * Reagent - Re-Com enabled\n\n* Framework\n  * Default header support\n  * Tab system\n  * Picture frames\n  * Session groups\n\"]\n])", :out-height "100px", :eid "ed-hanami", :height "700px"}}, :specs [{:usermeta {:frame {:top [[gap :size "20px"] [md {:style {:font-size "25px", :width 900}} "\n# Hanami\n\n* Library \n  * Enables construction of vis apps on top of Vega and Vega-Lite\n    * Analogous to Shiny or Dash\n  * Server/Client, and Client only apps\n  * Template system\n    * Templates parameterized by *substitution keys*\n      * Abstract visualizations\n    * Recursively transformed into legal VG/VGL specs (and other...)\n    * Default set of templates (aerial.hanami.templates)\n    * Default set of substitution keys (aerial.hanami.common)\n    * General transformer `xform`\n    * Completely changeable to suite your wants/needs\n  * Simple potent messaging\n  * Reagent - Re-Com enabled\n\n* Framework\n  * Default header support\n  * Tab system\n  * Picture frames\n  * Session groups\n"]], :pos :after, :at :end, :fid :f1}, :session-name "Exploring", :opts {:mode "vega-lite", :source false, :export {:png true, :svg true}, :renderer "canvas", :scaleFactor 1, :editor true}, :msgop :tabs, :tab {:label "Expl1", :id :expl1, :opts {:eltsper 2, :size "auto", :order :row}}}}]}} {:templates {:label "Templates", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :templates, :fn [quote editor-repl-tab], :layout :left-right, :ns aerial.saite.usercode, :width "730px", :src "(hc/xform {:a :X})\n(hc/get-default :X)\n\n(hc/xform {:a :X} :X \"foo\")\n(hc/xform {:a :X} :X {:y :Y})\n(hc/get-default :Y)\n\n(hc/xform {:a :Saite})\n(hc/get-default :Saite)\n(hc/xform {:a :Saite} :Saite 12)\n\n(hc/xform {:a :FOO} :FOO #{1 2})\n(hc/xform {:a :FOO} :FOO #{})\n\n(hc/get-default :XSCALE)\n(hc/xform {:a :FOO} :FOO {:scale :XSCALE})\n\n\n(hc/get-default :DATA)\n(hc/get-default :NDATA)\n(hc/get-default :UDATA)\n\n(hc/get-default :VALDATA)\n((hc/get-default :VALDATA) {:DATA \"one\"})\n((hc/get-default :VALDATA) {:UDATA \"data/cars.json\"})\n\nht/data-options\n(hc/xform ht/data-options)\n(hc/xform ht/data-options :DATA \"one\")\n(hc/xform ht/data-options :UDATA \"data/cars.json\")\n\nht/view-base\n\n(hc/get-default :ENCODING)\n(hc/xform {:encoding :ENCODING})\n\n(hc/get-default :USERDATA)\n(hc/xform (hc/get-default :USERDATA))\n\n(hc/get-default :TLBL)\n;;:TLBL #(-> :TID % name cljstr/capitalize)\n(hc/get-default :TID)\n(hc/xform {:label :TLBL})\n\n\n(hc/xform ht/view-base)\n(hc/xform ht/view-base :UDATA \"data/cars.json\")\n\nht/point-chart\nht/bar-chart\n\n(hc/xform\n ht/point-chart\n :UDATA \"data/cars.json\"\n :X \"Horsepower\" :Y \"Miles_per_Gallon\" :COLOR \"Origin\")\n\n\n\n(deref hc/_defaults)\n(require '[clojure.set :as set])\n(require '[clojure.string :as str])\n\n(let [mydefs (->> hc/_defaults deref\n               (mapv (fn[[k v]](vector (-> k name str/lower-case) k)))\n               (into {}) set/map-invert)]\n  (take 10 mydefs)\n  #_(hc/xform ht/view-base mydefs)\n  #_(hc/xform ht/point-chart mydefs))", :out-height "700px", :eid "ed-templates", :height "700px"}}, :specs []}} {:tabs {:label "Tabs", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :tabs, :$split 0, :fn [quote interactive-doc-tab], :ns aerial.saite.usercode, :width "730px", :src "\n(hc/xform\n ht/empty-chart :FID :f1\n :TOP '[\n [gap :size \"20px\"]\n [md {:style {:width 900 :font-size \"25px\"}}\n\"\n# Tabs\n\n* Auto structuring of document by sections, chapters, or pages\n  * Conceptually, each tab corresponds to one of these in the doc\n\n* Tab bodies can be further structured into columns and rows\n  * Each row or column has an element count to auto control layout\n  * An *element* is a *picture frame* (next up...)\n  * *Wrapping functions* support embedding bodies in app specific controls or other layout\n    * Examples: Saite interactive editor and doc tabs \n\n* *Extension tabs* are tabs whose body is user defined / app specific\n  * Intended to let developer use tabs but with their own layouts\n  * **note** tabs will still auto-render when moving among them\n  * Example: Saite convert tab\n  \n\"]\n        ])\n\n(def prevhw [(hc/get-default :HEIGHT) (hc/get-default :WIDTH)])\n(hc/update-defaults :HEIGHT 200 :WIDTH 250)\n(hc/update-defaults :HEIGHT (first prevhw) :WIDTH (last prevhw))\n\n(hc/xform\n ht/point-chart :FID :f2 :VID :v2\n :UDATA \"data/cars.json\"\n :X \"Horsepower\" :Y \"Miles_per_Gallon\" :COLOR \"Origin\")\n\n(hc/xform\n ht/bar-chart :FID :f3 :VID :v3\n :UDATA \"data/cars.json\"  :XBIN true :YAGG :mean\n :X \"Horsepower\" :Y \"Miles_per_Gallon\" :COLOR \"Origin\")\n\n(hc/xform\n ht/line-chart :FID :f4 :VID :v4\n :UDATA \"data/cars.json\" :XBIN true :YAGG :mean\n :X \"Horsepower\" :Y \"Miles_per_Gallon\" :COLOR \"Origin\")\n\n(hc/xform\n ht/layer-chart :FID :f5 :VID :v5\n :UDATA \"data/cars.json\"\n :LAYER [(hc/xform\n          ht/point-chart :FID :f2 :VID :v2 :OPACITY 0.3\n          :X \"Horsepower\" :Y \"Miles_per_Gallon\" :COLOR \"Origin\")\n         (hc/xform\n          ht/line-chart :XBIN true :YAGG :mean\n          :X \"Horsepower\" :Y \"Miles_per_Gallon\" :COLOR \"Origin\")])\n\n(hc/xform\n ht/line-chart :FID :f6 :VID :v6\n :UDATA \"data/cars.json\"\n :X \"Horsepower\" :Y \"Miles_per_Gallon\" \n :COLOR \"Origin\" ;:SHAPE \"Origin\"\n :COLDEF {:field \"Origin\"})\n\n", :out-height "100px", :eid "ed-tabs", :height "700px"}}, :specs [{:usermeta {:frame {:top [[gap :size "20px"] [md {:style {:font-size "25px", :width 900}} "\n# Tabs\n\n* Auto structuring of document by sections, chapters, or pages\n  * Conceptually, each tab corresponds to one of these in the doc\n\n* Tab bodies can be further structured into columns and rows\n  * Each row or column has an element count to auto control layout\n  * An *element* is a *picture frame* (next up...)\n  * *Wrapping functions* support embedding bodies in app specific controls or other layout\n    * Examples: Saite interactive editor and doc tabs \n\n* *Extension tabs* are tabs whose body is user defined / app specific\n  * Intended to let developer use tabs but with their own layouts\n  * **note** tabs will still auto-render when moving among them\n  * Example: Saite convert tab\n  \n"]], :pos :after, :at :end, :fid :f1}, :session-name "Exploring", :opts {:mode "vega-lite", :source false, :export {:png true, :svg true}, :renderer "canvas", :scaleFactor 1, :editor true}, :msgop :tabs, :tab {:label "Expl1", :id :expl1, :opts {:eltsper 2, :size "auto", :order :row}}}}]}} {:pframes {:label "Picture Frames", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :pframes, :$split 0, :fn [quote interactive-doc-tab], :ns aerial.saite.usercode, :width "730px", :src "\n(hc/xform\n ht/empty-chart :FID :f1\n :TOP '[\n [gap :size \"20px\"]\n [md {:style {:width 900 :font-size \"25px\"}}\n\"\n# Picture Frames\n\n* Basic element for tab bodies\n  * Composed of five parts\n    * Top, Bottom, Left, Right frame panels; central visualization\n\n![img](https://github.com/jsa-aerial/hanami/blob/master/resources/public/images/picture-frame-layout.png?raw=true)\n\n* Each part is optional, but at least one must be present\n  * Frames without a visualization part are called 'empty frames'\n\n* Each frame panel can be hiccup, re-com components, and/or Mark Down\n  * In Saite, MD can include LaTex\n\n* Frame definitions are included in the `usermeta` data\n\n* Frames can (should) have frame IDs and visualization IDs\n  * Substitution keys :FID and :VID\n* Frames may be added in 'bulk' or (as in Saite interactive tabs) added incrementally\n\n* Panel parts, in particular the vis, may be updated independently\n\"]\n])", :out-height "100px", :eid "ed-pframes", :height "700px"}}, :specs [{:usermeta {:frame {:top [[gap :size "20px"] [md {:style {:font-size "25px", :width 900}} "\n# Picture Frames\n\n* Basic element for tab bodies\n  * Composed of five parts\n    * Top, Bottom, Left, Right frame panels; central visualization\n\n![img](https://github.com/jsa-aerial/hanami/blob/master/resources/public/images/picture-frame-layout.png?raw=true)\n\n* Each part is optional, but at least one must be present\n  * Frames without a visualization part are called 'empty frames'\n\n* Each frame panel can be hiccup, re-com components, and/or Mark Down\n  * In Saite, MD can include LaTex\n\n* Frame definitions are included in the `usermeta` data\n\n* Frames can (should) have frame IDs and visualization IDs\n  * Substitution keys :FID and :VID\n* Frames may be added in 'bulk' or (as in Saite interactive tabs) added incrementally\n\n* Panel parts, in particular the vis, may be updated independently\n"]], :pos :after, :at :end, :fid :f1}, :session-name "Exploring", :opts {:mode "vega-lite", :source false, :export {:png true, :svg true}, :renderer "canvas", :scaleFactor 1, :editor true}, :msgop :tabs, :tab {:label "Expl1", :id :expl1, :opts {:eltsper 2, :size "auto", :order :row}}}}]}} {:saite {:label "Saite", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :saite, :$split 0, :fn [quote interactive-doc-tab], :ns aerial.saite.usercode, :width "730px", :src "\n(hc/xform\n ht/empty-chart :FID :f1\n :TOP '[\n [gap :size \"20px\"]\n [md {:style {:width 900 :font-size \"25px\"}}\n\"\n# Saite\n\n* History synopsis \n  * Originally just a cleaned up variant of the many 'server push to client' libs\n    * message system Hanasu much simpler and clean yet very potent\n  * Highly repetitive visualizations - how to fix?\n    * Functions? Methods? Protocols? ???\n    * Everything is just data - leverage that ... *templates*\n  * Highly repetive layouts\n    * Why am I writing the same / similar hiccup?\n    * Apps typically need two levels of organization - 'pages' and page bodies\n    * Tab system with auto body layout\n  * Why can't I have nice integrated external controls?\n    * Re-Com and app level extensions\n  * OK, is this an app or a lib???\n    * Split - Hanami lib parts; Saite application using that\n  * Hmmm why can't I have LaTex. And editors? And save / restore, and ...\n\"]\n])", :out-height "100px", :eid "ed-saite", :height "700px"}}, :specs [{:usermeta {:frame {:top [[gap :size "20px"] [md {:style {:font-size "25px", :width 900}} "\n# Saite\n\n* History synopsis \n  * Originally just a cleaned up variant of the many 'server push to client' libs\n    * message system Hanasu much simpler and clean yet very potent\n  * Highly repetitive visualizations - how to fix?\n    * Functions? Methods? Protocols? ???\n    * Everything is just data - leverage that ... *templates*\n  * Highly repetive layouts\n    * Why am I writing the same / similar hiccup?\n    * Apps typically need two levels of organization - 'pages' and page bodies\n    * Tab system with auto body layout\n  * Why can't I have nice integrated external controls?\n    * Re-Com and app level extensions\n  * OK, is this an app or a lib???\n    * Split - Hanami lib parts; Saite application using that\n  * Hmmm why can't I have LaTex. And editors? And save / restore, and ...\n"]], :pos :after, :at :end, :fid :f1}, :session-name "Exploring", :opts {:mode "vega-lite", :source false, :export {:png true, :svg true}, :renderer "canvas", :scaleFactor 1, :editor true}, :msgop :tabs, :tab {:label "Expl1", :id :expl1, :opts {:eltsper 2, :size "auto", :order :row}}}}]}} {:saite2 {:label "Saite current", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :saite2, :$split 0, :fn [quote interactive-doc-tab], :ns aerial.saite.usercode, :width "730px", :src "\n(hc/xform\n ht/empty-chart :FID :f1\n :TOP '[\n [gap :size \"20px\"]\n [md {:style {:width 900 :font-size \"25px\"}}\n\"\n# Saite Current State (more or less...)\n\n  * Starting to settle, but sill changing\n  * Picture frame panels may be editors for the central vis\n  * Editor enabled 'interactive' tabs\n    * Basic 'repl' type\n    * Interactive 'document' type\n    * Namespace per tab (but can span tabs)\n  * Save / restore\n    * it's all just data\n    * can restore from URL - publish\n  * Interactive tab manipulation controls (next)\n  * Format of document level 'mark down' evolving\n  * Configuration / customization\n  * Various server side support evolving\n    * read from files\n    * mix server code and client code\n    * auto sync of namespaces\n\"]\n])", :out-height "100px", :eid "ed-saite2", :height "700px"}}, :specs [{:usermeta {:frame {:top [[gap :size "20px"] [md {:style {:font-size "25px", :width 900}} "\n# Saite Current State (more or less...)\n\n  * Starting to settle, but sill changing\n  * Picture frame panels may be editors for the central vis\n  * Editor enabled 'interactive' tabs\n    * Basic 'repl' type\n    * Interactive 'document' type\n    * Namespace per tab (but can span tabs)\n  * Save / restore\n    * it's all just data\n    * can restore from URL - publish\n  * Interactive tab manipulation controls (next)\n  * Format of document level 'mark down' evolving\n  * Configuration / customization\n  * Various server side support evolving\n    * read from files\n    * mix server code and client code\n    * auto sync of namespaces\n"]], :pos :after, :at :end, :fid :f1}, :session-name "Exploring", :opts {:mode "vega-lite", :source false, :export {:png true, :svg true}, :renderer "canvas", :scaleFactor 1, :editor true}, :msgop :tabs, :tab {:label "Expl1", :id :expl1, :opts {:eltsper 2, :size "auto", :order :row}}}}]}} {:saite3 {:label "Saite Future/Road map", :opts {:order :row, :eltsper 1, :size "auto", :wrapfn {:tid :saite3, :$split 0, :fn [quote interactive-doc-tab], :ns aerial.saite.usercode, :width "730px", :src "\n(hc/xform\n ht/empty-chart :FID :f1\n :TOP '[\n [gap :size \"20px\"]\n [md {:style {:width 900 :font-size \"23px\"}}\n\"\n# Saite Future / Road Map (in no particular order)\n\n  * Much more editor stuff\n    * sexp slurp and spit for sure\n    * other editors - Vim and Sublime\n  * Block 'MD' support\n    * What and how?\n  * Integrate Neanderthal and Panthera\n    * These are definitely needed and higher priority\n    * Turnkey support (include MKL libs)\n  * Other language support\n    * Python\n      * PyHanasu\n      * No longer relevant with Panthera??\n    * R\n      * Depends on R-Interop (Daniel, Chris)\n      * Or RHanasu??\n  * Cytoscape\n    * Needed for *sophisticated* interactive graphs\n    * Vega - low priority\n  * Other vis libs integration??\n  * Full self installing JAR (with PREPL or NREPL support)\n  \n\"]\n])", :out-height "100px", :eid "ed-saite3", :height "700px"}}, :specs [{:usermeta {:frame {:top [[gap :size "20px"] [md {:style {:font-size "23px", :width 900}} "\n# Saite Future / Road Map (in no particular order)\n\n  * Much more editor stuff\n    * sexp slurp and spit for sure\n    * other editors - Vim and Sublime\n  * Block 'MD' support\n    * What and how?\n  * Integrate Neanderthal and Panthera\n    * These are definitely needed and higher priority\n    * Turnkey support (include MKL libs)\n  * Other language support\n    * Python\n      * PyHanasu\n      * No longer relevant with Panthera??\n    * R\n      * Depends on R-Interop (Daniel, Chris)\n      * Or RHanasu??\n  * Cytoscape\n    * Needed for *sophisticated* interactive graphs\n    * Vega - low priority\n  * Other vis libs integration??\n  * Full self installing JAR (with PREPL or NREPL support)\n  \n"]], :pos :after, :at :end, :fid :f1}, :session-name "Exploring", :opts {:mode "vega-lite", :source false, :export {:png true, :svg true}, :renderer "canvas", :scaleFactor 1, :editor true}, :msgop :tabs, :tab {:label "Expl1", :id :expl1, :opts {:eltsper 2, :size "auto", :order :row}}}}]}}]