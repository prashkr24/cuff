package com.srineere.airbridge.widget

/**
 * The type scale a widget draws at.
 *
 * The widgets used to pass raw numbers: eight different figure sizes, five
 * sub sizes and four kicker sizes, none of them derived from anything. A
 * Sleep tile and a Sleep widget are the same reading, and they were set in
 * two unrelated type systems.
 *
 * The small end is the app's own -- [Label] is its labelMedium at 11,
 * [Body] its bodyMedium at 13, [FigureL] its displaySmall at 26. The large
 * end is the widgets' own, because a card read at arm's length on a home
 * screen wants a figure the tab never needs.
 *
 * Pick the step that matches the role, never a number. The point of the
 * names is that the next face cannot invent a thirteenth size.
 *
 * Not everything belongs here: the figure in the middle of a dial is sized
 * to the arc around it, not to this scale, and says so where it is set.
 */
internal object WidgetText {
    /** The smallest label a strip can carry, and a footnote anywhere. */
    const val Caption = 9

    /** A kicker or a note on a card with room. The app's labelMedium. */
    const val Label = 11

    /** Running text, and a value beside its label. The app's bodyMedium. */
    const val Body = 13

    /** A figure that shares its line with something else. */
    const val FigureXS = 14

    /** A figure in a strip, where the card is one line tall. */
    const val FigureS = 17

    /** A figure on a row or a small square. */
    const val FigureM = 20

    /** The figure a card is about. The app's displaySmall. */
    const val FigureL = 26

    /** The figure a wide card is about, with room under it. */
    const val FigureXL = 34

    /** A square card given over to one number. */
    const val FigureHero = 40

    /** The largest, for a tall card read across a room. */
    const val FigureMax = 44
}
