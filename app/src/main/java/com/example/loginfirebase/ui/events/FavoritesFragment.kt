package com.example.loginfirebase.ui.events

import com.example.loginfirebase.R

class FavoritesFragment : UserEventsFragment() {
    override val titleResource = R.string.my_favorites_title
    override val subtitleResource = R.string.favorites_subtitle
    override val collection = "favorites"
}
