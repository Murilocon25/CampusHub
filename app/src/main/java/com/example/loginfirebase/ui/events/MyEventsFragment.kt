package com.example.loginfirebase.ui.events

import com.example.loginfirebase.R

class MyEventsFragment : UserEventsFragment() {
    override val titleResource = R.string.my_events_title
    override val subtitleResource = R.string.my_events_subtitle
    override val collection = "registrations"
}
