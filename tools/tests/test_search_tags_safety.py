from __future__ import annotations

import unittest

from daedalon_test_support import DAEDALON_ASSETS, DAEDALON_DATA, load_json


class SearchTagSafetyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tags = {
            kind: {
                path.stem: load_json(path)
                for path in (DAEDALON_DATA / f"tags/{kind}").glob("*.json")
            }
            for kind in ("blocks", "items")
        }
        # Item models and placed blockstates provide an independent public-item
        # inventory: helper blocks have no item, while the emblem has no block.
        cls.public_ids = {
            path.stem
            for path in (DAEDALON_ASSETS / "models/item").glob("*.json")
            if (DAEDALON_ASSETS / f"blockstates/{path.stem}.json").is_file()
        }
        cls.public_refs = {f"daedalon:{block_id}" for block_id in cls.public_ids}
        cls.resolved: dict[str, frozenset[str]] = {}

    def members(self, tag: str, visiting: tuple[str, ...] = ()) -> frozenset[str]:
        self.assertIn(tag, self.tags["items"], f"Missing tag: {tag}")
        self.assertNotIn(tag, visiting, f"Cyclic tag reference: {(*visiting, tag)}")
        if tag in self.resolved:
            return self.resolved[tag]
        values: set[str] = set()
        for value in self.tags["items"][tag]["values"]:
            self.assertIsInstance(value, str, (tag, value))
            if value.startswith("#"):
                self.assertTrue(value.startswith("#daedalon:"), (tag, value))
                values.update(self.members(value.removeprefix("#daedalon:"), (*visiting, tag)))
            else:
                self.assertIn(value, self.public_refs, (tag, value))
                values.add(value)
        result = frozenset(values)
        self.resolved[tag] = result
        return result

    def test_tag_graph_has_parity_and_only_finite_public_memberships(self) -> None:
        self.assertEqual(4678, len(self.public_refs))
        self.assertEqual(self.tags["blocks"], self.tags["items"])
        self.assertEqual(self.public_refs, self.members("daedalon_blocks"))
        for name, tag in self.tags["items"].items():
            with self.subTest(tag=name):
                self.assertIs(tag["replace"], False)
                self.assertEqual(len(tag["values"]), len(set(tag["values"])))
                self.assertTrue(self.members(name), "Search tags must not be empty")

    def test_every_public_decor_item_has_broad_search_categories(self) -> None:
        for name in ("decor", "decoration", "decorative", "architecture"):
            with self.subTest(tag=name):
                self.assertEqual(self.public_refs, self.members(name))
        for name in ("emblem", "fountain_basin_part", "capital_part", "monopteros_part"):
            self.assertNotIn(f"daedalon:{name}", self.public_refs)

    def test_material_categories_keep_stone_and_metals_separate(self) -> None:
        stone = self.members("stone")
        metal = self.members("metal")
        self.assertFalse(stone & metal)
        self.assertEqual(self.public_refs, stone | metal)
        self.assertEqual(stone, self.members("rock"))
        self.assertEqual(metal, self.members("metallic"))
        self.assertEqual(metal, self.members("bronze") | self.members("silver"))
        for kind in ("blocks", "items"):
            for name, expected in (("stone", stone), ("metal", metal)):
                shared = load_json(DAEDALON_DATA / f"../material/tags/{kind}/{name}.json")
                self.assertEqual(expected, set(shared["values"]), (kind, name))
        self.assertTrue(all(value.split(":")[1].startswith(("bronze_", "silver_")) for value in metal))
        self.assertFalse(any(value.split(":")[1].startswith(("bronze_", "silver_")) for value in stone))
        self.assertEqual(5, len(self.members("white")))
        self.assertTrue(self.members("white") <= stone)

    def test_weathered_search_exactly_matches_aged_stone(self) -> None:
        aged = frozenset(
            ref for ref in self.public_refs if "_aged_" in ref or ref.endswith("_aged")
        )
        self.assertEqual(2268, len(aged))
        for name in ("aged", "weathered", "worn"):
            self.assertEqual(aged, self.members(name), name)
        self.assertTrue(aged <= self.members("stone"))
        self.assertFalse(aged & self.members("metal"))

    def test_practical_shape_synonyms_resolve_to_the_intended_families(self) -> None:
        for alias, source in {
            "vase": "urn",
            "vessel": "urn",
            "bracket": "corbel",
            "column_capital": "capital",
            "pillar_capital": "capital",
            "column_head": "capital",
            "pedestal": "plinth",
            "statue_base": "plinth",
            "curved_bench": "exedra",
            "straight_bench": "hedra",
            "plant_pot": "anthophoros_planter",
            "flower_pot": "anthophoros_planter",
            "flowerpot": "anthophoros_planter",
            "cupola": "monopteros_dome",
            "gazebo": "monopteros_dome",
            "obelisk": "obeliskos_monument",
            "pinnacle": "finial",
        }.items():
            with self.subTest(alias=alias):
                self.assertEqual(self.members(source), self.members(alias))
        self.assertEqual(self.members("urn") | self.members("anthophoros_planter"), self.members("pot"))
        benches = self.members("exedra") | self.members("hedra")
        for name in ("bench", "seat", "seating"):
            self.assertEqual(benches, self.members(name), name)
        self.assertEqual(self.members("statue") | self.members("bust"), self.members("sculpture"))

    def test_byzantine_and_guilloche_search_both_find_architecture(self) -> None:
        expected = self.members("byzantine_capital") | self.members("byzantine_frieze")
        for name in ("byzantine", "guilloche"):
            self.assertEqual(expected, self.members(name), name)

    def test_greek_and_roman_names_find_every_finish_of_the_same_subject(self) -> None:
        # Independent identity table: aliases must not leak into other gods.
        equivalents = {
            "zeus": ("jupiter", "jove", "iuppiter"),
            "hera": ("juno",),
            "poseidon": ("neptune", "neptunus"),
            "demeter": ("ceres",),
            "athena": ("minerva", "athene"),
            "apollo": ("apollon",),  # Apollo has the same Roman name.
            "artemis": ("diana",),
            "ares": ("mars",),
            "aphrodite": ("venus",),
            "hephaestus": ("vulcan", "vulcanus", "hephaistos"),
            "hermes": ("mercury", "mercurius"),
            "dionysus": ("bacchus", "liber", "liber_pater", "dionysos"),
            "hestia": ("vesta",),
        }
        vocabulary = load_json(DAEDALON_ASSETS / "search_vocabulary.json")["families"]
        all_olympians: set[str] = set()
        for subject, aliases in equivalents.items():
            with self.subTest(subject=subject):
                expected = self.members(f"{subject}_statue") | self.members(f"{subject}_bust")
                self.assertEqual(112, len(expected))
                all_olympians.update(expected)
                self.assertEqual(expected, self.members(subject))
                for alias in aliases:
                    self.assertEqual(expected, self.members(alias), alias)
                for form in ("statue", "bust"):
                    keys = set(vocabulary[f"{subject}_{form}"])
                    self.assertTrue({subject, *aliases} <= keys, (subject, form))
        self.assertEqual(1456, len(all_olympians))
        self.assertEqual(all_olympians, self.members("olympian"))

        for subject, alias in (("helios", "sol"), ("heracles", "hercules"),
                               ("heracles", "herakles"), ("phaeton", "phaethon")):
            self.assertEqual(self.members(subject), self.members(alias), alias)
            self.assertTrue(self.members(subject) <= self.members("greek"))
        self.assertFalse(self.members("sol") & self.members("apollo"))
        self.assertFalse(self.members("sol") & self.members("phaeton"))
        self.assertFalse(self.members("mercury") & self.members("heracles"))
        self.assertFalse(self.members("helios") & self.members("olympian"))
        self.assertFalse(self.members("phaeton") & self.members("olympian"))
        self.assertEqual(
            all_olympians | self.members("helios") | self.members("heracles"),
            self.members("deity"),
        )
        self.assertEqual(self.members("deity"), self.members("god"))
        goddesses = set().union(*(self.members(name) for name in
                                 ("aphrodite", "artemis", "athena", "demeter", "hera", "hestia")))
        self.assertEqual(goddesses, self.members("goddess"))
        self.assertFalse(self.members("phaeton") & self.members("deity"))

    def test_greek_and_roman_names_are_localized_in_every_supported_language(self) -> None:
        greek_names = {
            "zeus": ("zeus", "Zeus", "Zeus"),
            "hera": ("hera", "Hera", "Hera"),
            "poseidon": ("poseidon", "Poseidon", "Poseidón"),
            "demeter": ("demeter", "Demeter", "Deméter"),
            "athena": ("athena", "Athene", "Atenea"),
            "apollo": ("apollo", "Apollo", "Apolo"),
            "artemis": ("artemis", "Artemis", "Artemisa"),
            "ares": ("ares", "Ares", "Ares"),
            "aphrodite": ("aphrodite", "Aphrodite", "Afrodita"),
            "hephaestus": ("hephaestus", "Hephaistos", "Hefesto"),
            "hermes": ("hermes", "Hermes", "Hermes"),
            "dionysus": ("dionysus", "Dionysos", "Dioniso"),
            "hestia": ("hestia", "Hestia", "Hestia"),
        }
        roman_names = {
            "jupiter": ("Jupiter", "Jupiter", "Júpiter"),
            "juno": ("Juno", "Juno", "Juno"),
            "neptune": ("Neptune", "Neptun", "Neptuno"),
            "ceres": ("Ceres", "Ceres", "Ceres"),
            "minerva": ("Minerva", "Minerva", "Minerva"),
            "diana": ("Diana", "Diana", "Diana"),
            "mars": ("Mars", "Mars", "Marte"),
            "venus": ("Venus", "Venus", "Venus"),
            "vulcan": ("Vulcan", "Vulkan", "Vulcano"),
            "mercury": ("Mercury", "Merkur", "Mercurio"),
            "bacchus": ("Bacchus", "Bacchus", "Baco"),
            "vesta": ("Vesta", "Vesta", "Vesta"),
            "hercules": ("Hercules", "Herkules", "Hércules"),
        }
        for index, language in enumerate(("en_us", "de_de", "es_es")):
            entries = load_json(DAEDALON_ASSETS / f"lang/{language}.json")
            for name, translations in (greek_names | roman_names).items():
                with self.subTest(language=language, name=name):
                    self.assertEqual(translations[index], entries[f"search.daedalon.tag.{name}"])

    def test_fountain_search_keeps_supported_plinths_and_excludes_metals(self) -> None:
        supported = frozenset(
            ref for ref in self.members("plinth") if not ref.split(":")[1].startswith(("bronze_", "silver_"))
        )
        self.assertEqual(270, len(supported))
        self.assertEqual(supported, self.members("fountain_plinth"))
        self.assertEqual(self.members("fountain"), self.members("water_feature"))
        self.assertTrue(supported <= self.members("fountain"))
        self.assertFalse(self.members("metal") & self.members("fountain"))
        self.assertFalse(self.members("metal") & self.members("water_feature"))

    def test_browser_manifest_covers_public_items_without_false_material_aliases(self) -> None:
        vocabulary = load_json(DAEDALON_ASSETS / "search_vocabulary.json")
        self.assertEqual({"families", "materials"}, set(vocabulary))
        families = vocabulary["families"]
        materials = vocabulary["materials"]
        self.assertEqual(30, len(materials))
        self.assertNotIn("aged", materials)
        reached_families: set[str] = set()
        for block_id in sorted(self.public_ids):
            with self.subTest(block_id=block_id):
                if block_id.startswith("statue_spartan_promachos_"):
                    # Published Spartan stone IDs keep their original layout;
                    # normalizing a browser lookup must never rename them.
                    material_path = block_id.removeprefix("statue_spartan_promachos_").removesuffix("_aged")
                    matches = [name for name in materials if material_path == name]
                    family = "spartan_promachos_statue"
                else:
                    matches = [name for name in materials if block_id.startswith(name + "_")]
                    family = block_id.removeprefix(matches[0] + "_").removeprefix("aged_") if matches else ""
                self.assertEqual(1, len(matches), "Every public item needs one known material")
                material = matches[0]
                self.assertIn(family, families)
                reached_families.add(family)
                ref = f"daedalon:{block_id}"
                for tag in (*materials[material], *families[family]):
                    self.assertIn(ref, self.members(tag), (block_id, tag))
                self.assertIn(material, materials[material])
                self.assertTrue({"decor", "architecture"} <= set(families[family]))
                self.assertNotIn("daedalon_blocks", families[family])
                self.assertNotIn("daedalon_blocks", materials[material])
        self.assertEqual(set(families), reached_families, "Manifest must not expose proxy or obsolete families")
        for group in vocabulary.values():
            for name, tags in group.items():
                self.assertTrue(tags, name)
                self.assertEqual(len(tags), len(set(tags)), name)

    def test_published_spartan_ids_keep_search_and_finish_memberships(self) -> None:
        legacy = {
            block_id for block_id in self.public_ids
            if block_id.startswith("statue_spartan_promachos_")
        }
        self.assertEqual(54, len(legacy))
        self.assertIn("statue_spartan_promachos_aganite", legacy)
        self.assertIn("statue_spartan_promachos_aganite_aged", legacy)
        self.assertNotIn("aganite_spartan_promachos_statue", self.public_ids)
        self.assertNotIn("aganite_aged_spartan_promachos_statue", self.public_ids)
        self.assertTrue({f"daedalon:{block_id}" for block_id in legacy} <= self.members("stone"))
        self.assertEqual(
            {f"daedalon:{block_id}" for block_id in legacy}
            | {"daedalon:bronze_spartan_promachos_statue", "daedalon:silver_spartan_promachos_statue"},
            self.members("statue_spartan_promachos"),
        )

    def test_every_browser_search_key_is_present_in_all_three_languages(self) -> None:
        vocabulary = load_json(DAEDALON_ASSETS / "search_vocabulary.json")
        tags = {"aged", "weathered", "worn"}
        for group in vocabulary.values():
            tags.update(tag for values in group.values() for tag in values)
        expected_keys = {f"search.daedalon.tag.{tag}" for tag in tags}
        key_sets = []
        for language in ("en_us", "de_de", "es_es"):
            entries = load_json(DAEDALON_ASSETS / f"lang/{language}.json")
            key_sets.append({key for key in entries if key.startswith("search.daedalon.")})
            for key in expected_keys | {"search.daedalon.plinth.fountain"}:
                with self.subTest(language=language, key=key):
                    self.assertIn(key, entries)
                    self.assertIsInstance(entries[key], str)
                    self.assertTrue(entries[key].strip())
                    self.assertNotEqual(key, entries[key])
        self.assertEqual(key_sets[0], key_sets[1])
        self.assertEqual(key_sets[0], key_sets[2])

    def test_browser_design_form_and_material_names_are_complete_and_distinct(self) -> None:
        vocabulary = load_json(DAEDALON_ASSETS / "search_vocabulary.json")
        forms = {"statues", "busts", "urns", "capitals", "corbels", "plinths", "finials", "friezes",
                 "panels", "benches", "planters", "domes", "basins", "fountains", "monuments"}
        expected_keys = {
            *(f"catalogue.daedalon.design.{family}" for family in vocabulary["families"]),
            *(f"catalogue.daedalon.material.{material}" for material in vocabulary["materials"]),
            *(f"catalogue.daedalon.form.{form}" for form in forms),
            "catalogue.daedalon.designs", "catalogue.daedalon.forms", "catalogue.daedalon.materials",
            "catalogue.daedalon.design_info",
        }
        localized_key_sets = []
        for language in ("en_us", "de_de", "es_es"):
            entries = load_json(DAEDALON_ASSETS / f"lang/{language}.json")
            self.assertTrue(expected_keys <= entries.keys(), language)
            localized_key_sets.append({key for key in entries if key.startswith("catalogue.daedalon.")})
            design_names = [entries[f"catalogue.daedalon.design.{family}"] for family in vocabulary["families"]]
            self.assertEqual(len(design_names), len(set(design_names)), language)
            for key in expected_keys:
                self.assertTrue(entries[key].strip(), (language, key))
                self.assertNotEqual(key, entries[key], (language, key))
            self.assertNotEqual(entries["catalogue.daedalon.design.zeus_statue"],
                                entries["catalogue.daedalon.design.zeus_bust"])
        self.assertEqual(localized_key_sets[0], localized_key_sets[1])
        self.assertEqual(localized_key_sets[0], localized_key_sets[2])


if __name__ == "__main__":
    unittest.main()
