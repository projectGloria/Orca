# Orca interface

The layout follows Seal's interface (GPL-3.0-or-later). A modal navigation drawer, opened from each screen's header, holds three destinations: Download queue, Library and Settings. Back returns from Settings to the previous destination and from Library to the queue.

The queue shows every download as a grid of media cards or as a list, with All, Downloading, Stopped and Finished filters. The floating "New download" button opens `LinkSheet` (`HomeContent.kt`), which takes the link and starts a download or opens `OptionsSheet.kt` for format and playlist choices. Library lists finished media with search and a Video/Audio filter; rows open the player and the overflow opens the action sheet (`MediaCards.kt`). Settings uses preference rows for downloads, updates and about.

Colors come from the system's dynamic palette (`theme/Theme.kt`, `theme/Color.kt`). Shared tokens in `theme/` define typography, shapes and spacing. `components/` holds the header, drawer, avatar, buttons, selection groups, preference rows and empty states. The Orca artwork (`drawable-nodpi/orca_artwork`) appears in the drawer, the update dialog and About.

Library keeps its search string and media filter during navigation. The link draft and open sheet are stored in saved instance state.
