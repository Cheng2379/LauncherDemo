package com.example.launcherdemo.config

/**
 * 卡片组件状态
 * @author cheng
 * @since 2025/4/14
 */
sealed class CardComponentState {

 // 迷你(默认状态)
 data object MINI: CardComponentState()

 // 中等(屏占比1/3)
 data object MEDIUM: CardComponentState()

 // 最大化(屏占比2/3)
 data object Large: CardComponentState()
}