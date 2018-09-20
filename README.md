# saite
Exploratory graphics and visualization system. 咲いて (in bloom). Built on top of hanami vega/vega-lite library

<a href="https://saite.github.io"><img src="https://github.com/jsa-aerial/saite/blob/master/resources/public/images/in-bloom.png" align="left" hspace="10" vspace="6" alt="saite logo" width="150px"></a>

**Saite** is a Clojure(Script) mini application for exploratory creation of interactive visualizations based in [Vega-Lite](https://vega.github.io/vega-lite/) (VGL) and/or [Vega](https://vega.github.io/vega/) (VG) specifications. These specifications are declarative and completely specified by _data_ (JSON maps). VGL compiles into the lower level grammar of VG which in turn compiles to a runtime format utilizting lower level runtime environments such as [D3](https://d3js.org/), HTML5 Canvas, and [WebGL](https://github.com/vega/vega-webgl-renderer).

Saite also functions as an example application built with [Hanami](https://github.com/jsa-aerial/hanami). As such it has all the capability of Hanami's _template_ system and recursive transformation of parameterized templates.

Saite also uses of the _tab system_ provided by Hanami for automatic tab constructtion and updates, plus the application specific tab capabilities of that system. Further, Saite makes use of Hanami's visualization messaging system, in particular, the `user-msg` multimethod with implementations for the `:app-init` user msg as well as the `:data` msg for streaming data plots/charts.

In addition, Saite also makes use of `default-header-fn` provided by Hanami. This creates a simple page header giving the 'session name' for a session as well as a input area to change the name of the session.

