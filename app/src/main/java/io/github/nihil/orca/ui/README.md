# Orca interface

Home starts a download, Downloads contains saved media and transfers, and Settings contains preferences and updates. The bottom navigation exposes all three destinations. Back returns from Settings to the previous destination and from Downloads to Home.

The dark navy scheme uses cyan for primary actions and progress. Material semantic colors apply to screens, lists, sheets and dialogs. Shared tokens in `theme/` define typography, surfaces and spacing. `components/` contains navigation, panels and action buttons. `HomeContent.kt` owns the link form and downloads summary. `OptionsSheet.kt` owns format and playlist selection.

Downloads maintains its search string and selected media filter during navigation. The link draft is stored in saved instance state. Saved rows open the player and expose file actions in the overflow menu. Transfers display queue state, progress and recovery actions. Playlist confirmation stays visible while the sheet scrolls.
