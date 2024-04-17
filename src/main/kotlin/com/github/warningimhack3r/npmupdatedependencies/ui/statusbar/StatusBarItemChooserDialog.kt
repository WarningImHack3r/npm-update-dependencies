package com.github.warningimhack3r.npmupdatedependencies.ui.statusbar

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class StatusBarItemChooserDialog(items: Collection<String>) : JDialog() {
    private val cleanedItems = items.map { item ->
        // file name + path in parentheses
        item.substringAfterLast("/") + if (item.contains("/")) {
            " (${item.substringBeforeLast("/")})"
        } else ""
    }
    private val itemList = JBList(cleanedItems)
    private var selectedItem: Int? = null
    private var lastListClickTime = 0L

    companion object {
        const val WIDTH = 300
    }

    init {
        // Configure the dialog
        title = "Select an item"
        isModal = true
        isResizable = false
        isUndecorated = true
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        // Configure the header
        val header = JLabel("Select an item (${items.size})")
        header.preferredSize = Dimension(WIDTH, 30)
        header.horizontalAlignment = SwingConstants.CENTER
        header.alignmentX = 0.5f

        // Configure the filter field
        val filterField = JTextField()
        filterField.preferredSize = Dimension(WIDTH, 30)
        filterField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE -> {
                        selectedItem = null
                        dispose()
                    }

                    KeyEvent.VK_ENTER -> {
                        selectedItem = itemList.selectedIndex
                        dispose()
                    }

                    KeyEvent.VK_UP -> {
                        itemList.selectedIndex = (itemList.selectedIndex - 1 + items.size) % items.size
                        itemList.ensureIndexIsVisible(itemList.selectedIndex)
                    }

                    KeyEvent.VK_DOWN -> {
                        itemList.selectedIndex = (itemList.selectedIndex + 1) % items.size
                        itemList.ensureIndexIsVisible(itemList.selectedIndex)
                    }
                }
            }

            override fun keyReleased(e: KeyEvent?) {
                if (e == null || e.isActionKey) return
                // Filter the list
                val filter = filterField.text
                itemList.selectedIndex = 0
                itemList.clearSelection()
                itemList.setListData(cleanedItems.filterIndexed { index, _ ->
                    items.elementAt(index).contains(filter, true)
                }.also {
                    header.text = "Select an item (${it.size})"
                }.toTypedArray())
            }
        })
        filterField.requestFocusInWindow()

        // Configure the item list
        itemList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                    border = BorderFactory.createEmptyBorder(2, 5, 2, 5)
                }
            }
        }
        itemList.selectedIndex = 0
        itemList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                selectedItem = itemList.selectedIndex
            }
        }
        itemList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                selectedItem = itemList.selectedIndex
                val now = System.currentTimeMillis()
                if (now - lastListClickTime < 500) {
                    dispose()
                } else {
                    lastListClickTime = now
                }
            }
        })
        itemList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE -> {
                        selectedItem = null
                        dispose()
                    }

                    KeyEvent.VK_ENTER -> {
                        selectedItem = itemList.selectedIndex
                        dispose()
                    }
                }
            }
        })
        val scrollPane = JBScrollPane(
            itemList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        scrollPane.preferredSize = Dimension(WIDTH, (itemList.getCellBounds(0, 0)?.height?.plus(1) ?: 20).let {
            if (items.size > 10) it * 10 else it * items.size
        })

        // Layout the dialog
        layout = BoxLayout(contentPane, BoxLayout.PAGE_AXIS)
        contentPane.add(header)
        contentPane.add(filterField)
        contentPane.add(scrollPane)
    }

    fun showAndGet(): Int? {
        pack()
        setLocationRelativeTo(null)
        isVisible = true
        return selectedItem
    }
}
