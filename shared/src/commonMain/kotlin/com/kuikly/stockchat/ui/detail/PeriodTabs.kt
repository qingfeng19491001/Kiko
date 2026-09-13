package com.kuikly.stockchat.ui.detail

import com.kuikly.stockchat.domain.analysis.TechnicalAnalysis
import com.kuikly.stockchat.domain.analysis.TrendBias
import com.kuikly.stockchat.domain.model.CapitalFlowData
import com.kuikly.stockchat.domain.model.Instrument
import com.kuikly.stockchat.domain.model.KLinePeriod
import com.kuikly.stockchat.domain.model.PeerUniverse
import com.kuikly.stockchat.domain.model.Quote
import com.kuikly.stockchat.domain.model.SelectedChartPoint
import com.kuikly.stockchat.domain.util.NumberFormat
import com.kuikly.stockchat.ui.components.Icon
import com.kuikly.stockchat.ui.components.IconKind
import com.kuikly.stockchat.ui.components.InstrumentAvatar
import com.kuikly.stockchat.ui.components.TagChip
import com.kuikly.stockchat.ui.theme.AppTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// region 周期 Tab

/**
 * KuiklyKLineChart 完整 Demo 同款周期栏：
 * 34f 高通栏 + 底部 1px 细线，选中项蓝色加粗；“更多 ▾”弹出分钟级 / 季K / 年K 菜单。
 */
fun ViewContainer<*, *>.PeriodTabs(
    selected: () -> KLinePeriod,
    periodMenuOpen: () -> Boolean,
    onTogglePeriodMenu: () -> Unit,
    onSelect: (KLinePeriod) -> Unit,
) {
    val activeBlue = Color(0xFF1677FFL)
    val inactiveGray = Color(0xFF8C8C8CL)
    View {
        attr { height(34f); backgroundColor(AppTheme.surface) }
        View {
            attr {
                absolutePositionAllZero()
                flexDirectionRow(); alignItemsCenter()
                borderBottom(Border(1f, BorderStyle.SOLID, Color(0xFFE5E7EBL)))
            }
            KLinePeriod.mainTabs.forEach { period ->
                vbind({ selected() == period }) {
                    View {
                        attr { flex(1f); height(34f); allCenter() }
                        event { click { onSelect(period) } }
                        Text {
                            attr {
                                val active = selected() == period
                                text(period.label)
                                fontSize(12f)
                                color(if (active) activeBlue else inactiveGray)
                                if (active) fontWeight600()
                            }
                        }
                    }
                }
            }
            // 更多 ▾
            vbind({ selected().isExtended }) {
                View {
                    attr { flex(1f); height(34f); allCenter(); flexDirectionRow() }
                    event { click { onTogglePeriodMenu() } }
                    val extended = selected().isExtended
                    val active = extended || periodMenuOpen()
                    Text {
                        attr {
                            text(if (extended) selected().label else "更多")
                            fontSize(12f)
                            color(if (active) activeBlue else inactiveGray)
                            if (active) fontWeight600()
                        }
                    }
                    Text { attr { text("▾"); fontSize(9f); marginLeft(2f); color(if (active) activeBlue else inactiveGray) } }
                }
            }
        }
    }
}

/** 叠在 K 线图上方，避免被 34f 周期栏和 List 裁掉。 */
fun ViewContainer<*, *>.PeriodMoreMenu(
    selected: () -> KLinePeriod,
    onSelect: (KLinePeriod) -> Unit,
) {
    val activeBlue = Color(0xFF1677FFL)
    View {
        attr {
            positionAbsolute(); top(34f); right(0f); zIndex(60)
            width(82f); padding(8f); flexDirectionColumn()
            borderRadius(8f); backgroundColor(AppTheme.surface)
            boxShadow(BoxShadow(0f, 2f, 8f, Color(0x1F000000L)))
        }
        KLinePeriod.extendedTabs.forEach { period ->
            vbind({ selected() == period }) {
                View {
                    attr {
                        height(30f); allCenter(); borderRadius(5f)
                        backgroundColor(if (selected() == period) Color(0x1A1677FFL) else Color.TRANSPARENT)
                    }
                    event { click { onSelect(period) } }
                    Text {
                        attr {
                            text(period.label)
                            fontSize(12f)
                            color(if (selected() == period) activeBlue else Color(0xFF595959L))
                        }
                    }
                }
            }
        }
    }
}

// endregion
