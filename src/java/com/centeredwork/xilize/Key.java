/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.util.HashMap;

/**
 * Keys used by Xilize2 to drive the translation are defined here.
 *
 * Programmer's note:  The identifier of the enumerated type is identical to the
 * key name.  That is, for example:
 *
 * <PRE>    <CODE>String keyName = Key._TargetFile_.name();</CODE></PRE>
 *
 * where name() is defined in Enum. Convenience methods in {@link Task } allow
 * Keys to be used in place of Strings.
 * @see Task#isDefined(Key)
 * @see Task#define(Key,String)
 * @see Task#value(Key)
 */
public enum Key {
    
    // rename with refactoring tool to change
    
    _CommandLine_,
    _SingleThread_,
    _XilizeConfigFile_,
    _Natural_("true"),
    _TargetFile_,
    _TargetRoot_,
    _TargetDir_,
    _TargetTree_,
    _TargetBranch_,
    
    _Root_,         // full path to top-level target
    _ProjectRoot_("./"),  // relative path from current dir to root
//    _NotRoot_,      // set to true if current dir is not the root dir
    
    _SingleFile_,   // full path to single file being xilized
    
    _DirPath_,      // absolute path to current dir
    _DirName_,      // name of current dir
    _DirLabel_,     // display label for current dir, default: _DirName_
    _DirLabelList_, // chain of labels from the root to current dir
    _DirLabelSeparator_("/"), // separator string used in _DirLabelList_
    _DirLabelListLinked_,
    _DirHome_("index.html"),
    _DirInclude_,                               // comma-separated subdir names to include
    _DirExclude_("pics, img, images, signatures,"), // comma-separated subdir names to exclude
    _SubDirList_,
    _SubDirListLinked_,
    _SubDirListSeparator_(" "),
    
    _Prev_,         // for page ordering
    _Next_,
    _PageNumber_,
    _PagesTotal_,
    
    _FilePathXil_,
    _FileNameXil_,
    _FilePathHtml_,
    _FileNameHtml_,
    _FilePathOuput_,
    _FileNameOutput_,
    
    
    _DebugReportRawBlocks_("false"),
    
    _OutputDirectory_,              // todo: use this
    _InputExtension_("xil"),        // todo: use this
    _OutputExtension_("html"),      // todo: use this
    
    _LineCommentString_(">xil>"),
    _BlockStartString_("{{"),
    _BlockEndString_("}}"),
    _SpacesPerTab_("4"),
    _PreStringWrap_("0"),
    _IdPrefix_("xil_"),             // used to create id's for heading anchors
    
    _UnsignedBlockSigName_("anonymous"),
    _UnsignedBlockSigSubstitute_("p"),
    
    _NaturalLabel_,         // text of the first anonymous block
    title,                  // deprecated in 2.0, legacy support, same as _NaturalLabel_
    _NaturalSig_("h1"),     // signature to use for first anonymous block
    
    prolog("true"),
    epilog("true"),
    doctype("strict"),
    charset("iso-8859-1"),
    css,
    commoninc,
    headerinc,
    footerinc,
    style,
    script,
    
    _BodyTagAttributes_,    // tag attributes for the body start tag <body>
    
    _FootnoteStyle_("modern"),
    
    _WarnOnSigOverride_("true"),
    _NoWarnOnLooksLikeSig_,
    _Silent_,
    _NoWarn_,
    _Debug_,
    
    ;  // end of keys ********************************************************
    
    private String init;    // null if no initial/default value

    /**
     * constructs a key with an initial (default) value
     * @param value the inital (default) value for this key
     */
    Key(String value) { init = value; }
    
    /**
     * constructs a key with no initial (default) value
     */
    Key() {}
    
    /** get's the key's initial (default) value
     *
     * @return the key's initial value
     */
    public String initValue() { return init; }
    
    /**
     * for those keys that have an initial value, adds them to given map
     * @param map the map to initialize
     */
    public static void addDefaultKV( HashMap<String,String> map ) {
        for( Key k : values() ) {
            if( k.init != null )
                map.put(k.name(), k.initValue());
        }
    }
    
    
}
