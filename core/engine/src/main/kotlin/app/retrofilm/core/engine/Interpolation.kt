package app.retrofilm.core.engine

/** Sposób interpolacji między węzłami LUT 3D. */
enum class Interpolation {
    /** 8 węzłów sześcianu (domyślna). */
    TRILINEAR,

    /** 4 węzły czworościanu (6 czworościanów w sześcianie; mniej rozmyć na przekątnej szarości). */
    TETRAHEDRAL
}
