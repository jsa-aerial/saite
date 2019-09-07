
## Editor Keys

* The following are 'control' sequences for editor operations

### Text manipulation

  * `Ctrl-S` : regular expression search. Type in expression, then `return`. Match will be highlighted. Repeat `ctrl-s` to find more examples. Click in editor body to end.

  * `Ctrl-X R` : regular expression replace. Type in expression to replace, then `return`. Type in replacement. Use listed controls to choose which to replace and to stop.

  * `Ctrl-Home` : Move cursor to line 1, character 0

  * `Ctrl-End`  : Move cursor to last line and last character

  * `Ctrl-F` : Move forward sexpression

  * `Ctrl-B` : Move backward sexpression

  * `Shift-[an arrow key]` : make selection (for cutting, copying)

  * `Alt-W` : Cut current selection

  * `Alt-K` : Cut/kill forward sexpression

  * `Ctrl-Y` : Yank last cut to cursor position

  * `Ctrl-/` : Undo last change. Repeat for more.


### Code execution

  * `Ctrl-X Ctrl-E` : Evaluate last sexpression

  * `Ctrl-X Ctrl-C` : Evaluate outer sexpression at cursor


### Frame visualization

  * `Ctrl-X Ctrl-I` : Insert frame defined by outer sexpression at cursor
  * `Insert` : synonym for `ctrl-x ctrl-i`

  * `Ctrl-X Ctrl-D` : Delete frame associated with outer sexpression at cursor
  * `Delete` : synonym for `ctrl-x ctrl-d`

  * `Ctrl-X Ctrl-V` : Repaint vis associated with frame at cursor

  * `Ctrl-Alt-W` : Enhanced cut - will remove associated frame if visible
  * `Ctrl-Alt-Y` : Enhanced yank - will insert (in position) associated frame if *not* visible


## Control Bar