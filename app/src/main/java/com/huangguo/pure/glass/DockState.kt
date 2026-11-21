package com.huangguo.pure.glass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Java ⇄ Compose 的 Dock 状态桥。
 *
 * Java 侧 setSelected / setLight 后 Compose 自动重组；Compose 侧点击回写 selected 后
 * Java 侧读 getSelected() 拿到当前页。所有字段都是 Compose State，跨线程写会抛异常，
 * 只在主线程改。
 */
class DockState {

    /** 当前选中的 Tab 索引 */
    var selected by mutableIntStateOf(0)

    /** 浅色主题（Compose 侧据此选 accent / 表面色） */
    var light by mutableStateOf(false)

    /** 图标基础色 ARGB */
    var iconTint by mutableIntStateOf(0xFFF2F4F8.toInt())

    /** 文字基础色 ARGB */
    var labelColor by mutableIntStateOf(0xFFF2F4F8.toInt())

    /** 选中态着色（指示器玻璃采样着色层得到这个颜色） */
    var accent by mutableIntStateOf(0xFF0091FF.toInt())
}
