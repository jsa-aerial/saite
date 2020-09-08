
## Defaults keys in default config.edn.

Anything listed here will override the defaults from Base Emacs, otherwise you get the values from Base Emacs

**NOTE**: the default paredit bindings do not currently get bound by default. If you want those you will need to put them in your config.edn

* "Ctrl-F"             `pe/forward-sexp`
* "Ctrl-B"             `pe/backward-sexp`
* "Ctrl-Left"          `pe/forward-barf-sexp`
* "Ctrl-Right"         `pe/forward-slurp-sexp`
* "Ctrl-Alt-Left"      `pe/backward-barf-sexp`
* "Ctrl-Alt-Right"     `pe/backward-slurp-sexp`

* "Ctrl-Home"          `em/go-doc-start`
* "Ctrl-End"           `em/go-doc-end`
* "Ctrl-L"             `recenter-top-bottom`
* "Ctrl-X D"           `show-doc`
* "Ctrl-X S"           `show-source`

* "Alt-W"              `enhanced-cut`
* "Ctrl-Y"             `enhanced-yank`
* "Alt-K"              `em/kill-sexp`
* "Ctrl-X R"           `em/query-replace`
* "Ctrl-X Ctrl-B"      `clear-output`

* "Ctrl-Alt-T"         `insert-txt-frame`
* "Ctrl-Alt-C"         `insert-cm-md`
* "Ctrl-Alt-M"         `insert-md`
* "Ctrl-Alt-V"         `insert-vis-frame`
* "Ctrl-Alt-W"         `enhanced-cut`
* "Ctrl-Alt-Y"         `enhanced-yank`
* "Ctrl-X Ctrl-I"      `insert-frame`
* "Insert"             `insert-frame`
* "Ctrl-X Ctrl-D"      `delete-frame`
* "Delete"             `delete-frame`
* "Ctrl-X Ctrl-V"      `re-visualize`

* "Ctrl-X X"           `xform-code`
* "Ctrl-X Ctrl-E"      `evalxe`
* "Ctrl-X Ctrl-C"      `eval-mixed-cc` ; `evalcc`
* "Ctrl-X J"           `evaljvm-xe`
* "Ctrl-X Ctrl-J"      `evaljvm-cc`
* "Ctrl-X Ctrl-M"      `eval-mixed-cc`


## Base Emacs

**NOTE** browsers can screwup some of these. In particular `Ctrl-C` as a prefix does not seem to work - at least for me

**NOTE2** some of these do not currently make sense/do anything in the browser. For example `save-doc` and `open-doc` do not do anything.

* "Ctrl-W"             `em/kill-region`
* "Ctrl-K"             `em/kill-line`
* "Alt-W"              `em/kill-ring-save`

* "Ctrl-Y"             `em/yank`
* "Alt-Y"              `em/yank-pop`

* "Ctrl-Space"         `em/set-mark`
* "Ctrl-X H"           `em/select-all`

* "Ctrl-F"             `em/forward-char`
* "Ctrl-B"             `em/backward-char`

* "Ctrl-D"             `em/delete-forward-char`
* "Backspace"          `em/delete-backward-char`

* "Alt-F"              `em/forward-word`
* "Alt-B"              `em/backward-word`
* "Alt-D"              `em/forward-kill-word`
* "Alt-Backspace"      `em/backward-kill-word`

* "Ctrl-N"             `em/next-line`
* "Ctrl-P"             `em/previous-line`

* "Ctrl-A"             `em/beginning-of-line`
* "Ctrl-E"             `em/end-of-line`

* "Alt-V"              `em/scroll-down`
* "Ctrl-V"             `em/scroll-up`
* "PageUp"             `em/page-up`
* "PageDown"           `em/page-down`

* "Ctrl-Up"            `em/backward-paragraph`
* "Ctrl-Down"          `em/forward-paragraph`

* "Alt-A"              `em/backward-sentence`
* "Alt-E"              `em/forward-sentence`
* "Alt-K"              `em/kill-sentence`

* "Ctrl-Alt-K"         `em/kill-sexp`
* "Ctrl-Alt-Backspace" `em/kill-back-sexp`

* "Ctrl-Alt-F"         `em/forward-sexp`
* "Ctrl-Alt-B"         `em/backward-sexp`

* "Shift-Ctrl-Alt-2"   `em/mark-sexp`
* "Ctrl-Alt-T"         `em/transpose-sexps`
* "Ctrl-Alt-U"         `em/backward-up-list`

* "Alt-Space"          `em/just-one-space`
* "Ctrl-O"             `em/open-line`
* "Ctrl-T"             `em/transpose-chars`

* "Alt-C"              `em/capitalize-word`
* "Alt-U"              `em/uppercase-word`
* "Alt-L"              `em/downcase-word`

* "Alt-;"              `em/toggle-comment`

* "Ctrl-/"             `em/undo`
* "Shift-Ctrl--"       `em/repeat-undo`
* "Ctrl-Z"             `em/undo`
* "Cmd-Z"              `em/repeat-undo`

* "Shift-Alt-,"        `em/go-doc-start`
* "Shift-Alt-."        `em/go-doc-end`
* "Alt-G G"            `em/goto-line`

* "Ctrl-S"             `em/search-forward`
* "Ctrl-R"             `em/search-backward`
* "Shift-Alt-5"        `em/query-replace`

* "Enter"              `em/newline-indent`
* "Ctrl-J"             `em/electric-newline-indent`
* "Tab"                `em/indent-auto`
* "Ctrl-X Tab"         `em/indent-selection`
* "Ctrl-X Ctrl-X"      `em/exchange-point-mark`

* "Ctrl-X Ctrl-S"      `em/save-doc`
* "Ctrl-X F"           `em/open-doc`

* "Alt-/"              `em/autocomplete`

* "Ctrl-G"             `em/keyboard-quit`



## Paredit

**NOTE** some of these may be shadowed by the above

* "Shift-9"            `pe/open-round`
* "Shift-0"            `pe/close-round`
* "Shift-Alt-0"        `pe/close-round-and-newline`
* "["                  `pe/open-square`
* "]"                  `pe/close-square`
* "Shift-["            `pe/open-brace`
* "Shift-]"            `pe/close-brace`
* "Shift-Alt-'"        `pe/meta-doublequote`
* "Shift-Alt-;"        `pe/comment-dwim`

* "Delete"             `pe/forward-delete`
* "Backspace"          `pe/backward-delete`

* "Alt-d"              `pe/forward-kill-word`
* "Ctrl-Delete"        `pe/forward-kill-word`
* "Ctrl-Backspace"     `pe/backward-kill-word`
* "Alt-Backspace"      `pe/backward-kill-word`

* "Ctrl-Alt-f"         `pe/forward`
* "Ctrl-Alt-b"         `pe/backward`
* "Ctrl-Alt-u"         `pe/backward-up`
* "Ctrl-Alt-d"         `pe/forward-down`
* "Ctrl-Alt-p"         `pe/backward-down`
* "Ctrl-Alt-n"         `pe/forward-up`
* "Shift-Ctrl-f"       `pe/forward-sexp`
* "Shift-Ctrl-b"       `pe/backward-sexp`
* "Shift-Alt-9"        `pe/wrap-round`
* "Alt-s"              `pe/splice-sexp`
* "Alt-Up"             `pe/splice-sexp-killing-backward`
* "Alt-Down"           `pe/splice-sexp-killing-forward`
* "Alt-r"              `pe/raise-sexp`
* "Shift-Ctrl-0"       `pe/forward-slurp-sexp`
* "Shift-Ctrl-]"       `pe/forward-barf-sexp`
* "Shift-Ctrl-9"       `pe/backward-slurp-sexp`
* "Shift-Ctrl-["       `pe/backward-barf-sexp`
* "Shift-Alt-s"        `pe/split-sexp`
* "Shift-Alt-j"        `pe/join-sexps`
* "Alt-q"              `pe/reindent-defun`

