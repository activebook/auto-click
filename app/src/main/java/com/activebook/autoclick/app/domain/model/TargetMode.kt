package com.activebook.autoclick.app.domain.model

/** How the tap target is expressed: a cell centre (coarse) or an exact point (precise). */
enum class TargetMode {
    /** `cellIndex` wins; the tap lands in the centre of that ninth of the screen. */
    CELL,

    /** `point` wins; the tap lands exactly there, clamped to the usable area. */
    POINT,
}
