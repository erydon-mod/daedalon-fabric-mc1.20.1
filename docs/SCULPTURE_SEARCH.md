# Sculpture search names

See [item browser search and groups](ITEM_BROWSER_SEARCH.md) for catalogue
navigation and the plain `block` search rule.

Statues and busts keep their Greek display names and registry IDs. Searching by
the corresponding Roman name finds every material and aged finish of the same
subject in Creative, REI, JEI and EMI.

| Greek name | Roman search name |
| --- | --- |
| Zeus | Jupiter, Jove |
| Hera | Juno |
| Poseidon | Neptune |
| Demeter | Ceres |
| Athena | Minerva |
| Apollo | Apollo |
| Artemis | Diana |
| Ares | Mars |
| Aphrodite | Venus |
| Hephaestus | Vulcan |
| Hermes | Mercury |
| Dionysus | Bacchus, Liber, Liber Pater |
| Hestia | Vesta |
| Helios | Sol |
| Heracles | Hercules |

The same shared vocabulary includes established alternate spellings such as
Apollon, Athene, Dionysos, Hephaistos, Herakles, Phaeton and Phaethon. Phaeton
keeps his own name; he is not an alias for Helios or Apollo. Helios and Apollo
also remain distinct search subjects. Helios and Phaeton are Greek subjects but
are excluded from the thirteen-member Olympian-plus-Hestia category.

All three supported languages receive translated search terms. For example,
Mercury/Merkur/Mercurio, Vulcan/Vulkan/Vulcano and Bacchus/Bacchus/Baco resolve
to the same subjects. English terms remain searchable alongside the active
locale's translations. `god` and `deity` include the Olympians, Helios and the
deified hero Heracles; `goddess` selects the six goddesses. Phaeton is not added
to the deity category.

`tools/generate_daedalon_assets.py` owns the subject aliases, generated block/item
tags, compact `search_vocabulary.json` and locale search entries. The existing
`DaedalonSearchVocabulary` cache feeds all four search surfaces. No registry
scan, per-tick work, rendering hook or visible hover-tooltip addition is needed.

Verification covers exact subject membership for every finish, localization,
the bundled Java search index and separation of similarly named subjects. Live
Creative/REI/JEI/EMI checks are still required to confirm each installed viewer.

Identity references:

- [National Gallery of Art, classical mythology teaching packet](https://www.nga.gov/sites/default/files/migrate_images/content/dam/ngaweb/education/learning-resources/teaching-packets/pdfs/classical-mythology-tp.pdf)
- [Wallace Collection, Greek and Roman names including Jove and Hercules](https://www.wallacecollection.org/documents/517/Myths_Teaching_Resources_-_Introduction.pdf)
- [Fitzwilliam Museum, Liber/Bacchus/Dionysos](https://www.fitzmuseum.cam.ac.uk/index.php/explore-our-collection/highlights/GR11835)
- [British Museum, Helios/Sol](https://www.britishmuseum.org/collection/object/Y_EA49647)
- [Torlonia Collection, Hestia/Vesta tradition](https://www.fondazionetorlonia.org/statua-di-divinita-con-peplo-detta-hestia-giustiniani)
