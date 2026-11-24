package com.client.androidnga.core.data.bean

import gov.anzong.androidnga.common.base.JavaBean

data class ThreadPageBean(
    @JvmField
    var author: String?,
    @JvmField
    var authorid: Int,
    @JvmField
    var digest: Int,
    @JvmField
    var fid: Int,
    @JvmField
    var jdata: String?,
    @JvmField
    var lastmodify: Int,
    @JvmField
    var lastpost: Int,
    @JvmField
    var lastposter: String?,
    @JvmField
    var locked: Int,
    @JvmField
    var post_misc_var: String?,
    @JvmField
    var postdate: Int,
    @JvmField
    var quote_from: Int,
    @JvmField
    var quote_to: String?,
    @JvmField
    var recommend: Int,
    @JvmField
    var replies: Int,
    @JvmField
    var subject: String?,
    @JvmField
    var this_visit_rows: Int,
    @JvmField
    var tid: Int,
    @JvmField
    var topic_misc: String?,
    @JvmField
    var tpid: Int,
    @JvmField
    var type: Int
) : JavaBean