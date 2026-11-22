"""French and Japanese catalogue translations, generated without English fallbacks.

Material and named design spellings remain stable across the Collection. New
English UI strings or family names must receive an explicit translation here.
"""
from __future__ import annotations

import json
from pathlib import Path

CLIENT_SETTINGS_TRANSLATIONS = json.loads(
    (Path(__file__).resolve().parents[1] / "config/client-settings-language.json").read_text(encoding="utf-8")
)


LOCALES = ("fr_fr", "ja_jp")

# English family: (French, Japanese, French grammatical gender).
DESIGNS = {
    "Amphora Urn": ("Urne Amphora", "Amphoraの壺", "f"),
    "Konche Urn": ("Urne Konche", "Koncheの壺", "f"),
    "Diota Urn": ("Urne Diota", "Diotaの壺", "f"),
    "Kylix Urn": ("Urne Kylix", "Kylixの壺", "f"),
    "Kalyx Urn": ("Urne Kalyx", "Kalyxの壺", "f"),
    "Lekythos Urn": ("Urne Lekythos", "Lekythosの壺", "f"),
    "Pelike Urn": ("Urne Pelike", "Pelikeの壺", "f"),
    "Pithos Urn": ("Urne Pithos", "Pithosの壺", "f"),
    "Rhabdos Urn": ("Urne Rhabdos", "Rhabdosの壺", "f"),
    "Salpinx Urn": ("Urne Salpinx", "Salpinxの壺", "f"),
    "Stamnos Urn": ("Urne Stamnos", "Stamnosの壺", "f"),
    "Baroque Corbel": ("Corbeau baroque", "バロック様式の持ち送り", "m"),
    "Corinthian Corbel": ("Corbeau corinthien", "コリント様式の持ち送り", "m"),
    "Georgian Corbel": ("Corbeau géorgien", "ジョージアン様式の持ち送り", "m"),
    "Ionic Corbel": ("Corbeau ionique", "イオニア様式の持ち送り", "m"),
    "Renaissance Corbel": ("Corbeau Renaissance", "ルネサンス様式の持ち送り", "m"),
    "Byzantine Capital": ("Chapiteau byzantin", "ビザンティン様式の柱頭", "m"),
    "Corinthian Capital": ("Chapiteau corinthien", "コリント様式の柱頭", "m"),
    "Gothic Capital": ("Chapiteau gothique", "ゴシック様式の柱頭", "m"),
    "Greek Ionic Capital": ("Chapiteau ionique grec", "ギリシャ・イオニア式の柱頭", "m"),
    "Roman Composite Capital": ("Chapiteau composite romain", "ローマ・コンポジット式の柱頭", "m"),
    "Tuscan Capital": ("Chapiteau toscan", "トスカナ様式の柱頭", "m"),
    "Georgian Lantern": ("Lanterne géorgienne", "ジョージアン様式のランタン", "f"),
    "Gothic Lantern": ("Lanterne gothique", "ゴシック様式のランタン", "f"),
    "Byzantine Lantern": ("Lanterne byzantine", "ビザンティン様式のランタン", "f"),
    "Gothic Panel": ("Panneau gothique", "ゴシック様式のパネル", "m"),
    "Corinthian Panel": ("Panneau corinthien", "コリント様式のパネル", "m"),
    "Byzantine Panel": ("Panneau byzantin", "ビザンティン様式のパネル", "m"),
    "Georgian Panel": ("Panneau géorgien", "ジョージアン様式のパネル", "m"),
    "Renaissance Panel": ("Panneau Renaissance", "ルネサンス様式のパネル", "m"),
    "Byzantine Frieze": ("Frise byzantine", "ビザンティン様式のフリーズ", "f"),
    "Gothic Frieze": ("Frise gothique", "ゴシック様式のフリーズ", "f"),
    "Corinthian Frieze": ("Frise corinthienne", "コリント様式のフリーズ", "f"),
    "Ionic Frieze": ("Frise ionique", "イオニア様式のフリーズ", "f"),
    "Anthophoros Planter": ("Jardinière Anthophoros", "Anthophorosのプランター", "f"),
    "Monopteros Dome": ("Dôme Monopteros", "Monopterosのドーム", "m"),
    "Hedra Bench": ("Banc Hedra", "Hedraのベンチ", "m"),
    "Exedra": ("Exèdre", "Exedra", "f"),
    "Balanos Finial": ("Fleuron Balanos", "Balanosの頂華", "m"),
    "Kynara Finial": ("Fleuron Kynara", "Kynaraの頂華", "m"),
    "Phlox Finial": ("Fleuron Phlox", "Phloxの頂華", "m"),
    "Sphaira Finial": ("Fleuron Sphaira", "Sphairaの頂華", "m"),
    "Strobilos Finial": ("Fleuron Strobilos", "Strobilosの頂華", "m"),
    "Louterion Basin": ("Bassin Louterion", "Louterionの水盤", "m"),
    "Pege Fountain": ("Fontaine Pege", "Pegeの噴水", "f"),
    "Krene Fountain": ("Fontaine Krene", "Kreneの噴水", "f"),
    "Gothic Fountain Basin": ("Bassin de fontaine gothique", "ゴシック様式の噴水の水盤", "m"),
    "Georgian Fountain Basin": ("Bassin de fontaine géorgien", "ジョージアン様式の噴水の水盤", "m"),
    "Greek Fountain Basin": ("Bassin de fontaine grec", "ギリシャ様式の噴水の水盤", "m"),
    "Obeliskos Monument": ("Monument Obeliskos", "Obeliskosの記念碑", "m"),
    "Spartan Promachos Statue": ("Statue spartiate Promachos", "スパルタのPromachos像", "f"),
    "Lion Couchant Statue": ("Statue de lion couché", "伏せたライオンの像", "f"),
    "Lion Statant Statue": ("Statue de lion debout", "立ったライオンの像", "f"),
    "Olympian God Statue": ("Statue de dieu de l’Olympe", "オリュンポスの神の像", "f"),
    "Astragalos Plinth": ("Socle Astragalos", "Astragalosの台座", "m"),
    "Bathron Plinth": ("Socle Bathron", "Bathronの台座", "m"),
    "Kion Plinth": ("Socle Kion", "Kionの台座", "m"),
    "Stephanos Plinth": ("Socle Stephanos", "Stephanosの台座", "m"),
    "Triphyllon Plinth": ("Socle Triphyllon", "Triphyllonの台座", "m"),
}

# Mythological names use their customary localized spellings.
SUBJECTS = {
    "Aphrodite": ("Aphrodite", "アフロディテ"),
    "Apollo": ("Apollon", "アポロン"),
    "Ares": ("Arès", "アレス"),
    "Artemis": ("Artémis", "アルテミス"),
    "Athena": ("Athéna", "アテナ"),
    "Demeter": ("Déméter", "デメテル"),
    "Dionysus": ("Dionysos", "ディオニュソス"),
    "Helios": ("Hélios", "ヘリオス"),
    "Hephaestus": ("Héphaïstos", "ヘパイストス"),
    "Hera": ("Héra", "ヘラ"),
    "Hermes": ("Hermès", "ヘルメス"),
    "Hestia": ("Hestia", "ヘスティア"),
    "Poseidon": ("Poséidon", "ポセイドン"),
    "Zeus": ("Zeus", "ゼウス"),
    "Bellerophon": ("Bellérophon", "ベレロポン"),
    "Heracles": ("Héraclès", "ヘラクレス"),
    "Orpheus": ("Orphée", "オルフェウス"),
    "Perseus": ("Persée", "ペルセウス"),
    "Phaeton": ("Phaéton", "パエトン"),
    "Theseus": ("Thésée", "テセウス"),
}
for _subject, (_fr, _ja) in SUBJECTS.items():
    _of = "d’" if _fr[0] in "AÉIOU" or _fr.startswith("H") else "de "
    DESIGNS[f"{_subject} Statue"] = (f"Statue {_of}{_fr}", f"{_ja}の像", "f")
    DESIGNS[f"{_subject} Bust"] = (f"Buste {_of}{_fr}", f"{_ja}の胸像", "m")

MATERIALS = {
    name: (name, name) for name in (
        "Aganite", "Aterzon", "Borealis", "Brectite", "Calacattum", "Chalstrom",
        "Chrysonyx", "Etruscus", "Gelastrum", "Glacium", "Hesperion", "Imperium",
        "Kylorion", "Kelastrion", "Latmion", "Laurentium", "Mielonyx", "Nerium",
        "Noxoplis", "Porphyros", "Psamatheon", "Portorium", "Rosinium", "Sanguenite",
        "Selenephos", "Solistra", "Striatus",
    )
}
MATERIALS.update({"Bronze": ("bronze", "青銅"), "Silver": ("argent", "銀"), "White": ("blanc", "白色"), "Black": ("noir", "黒色")})

# Complete phrases, rather than word substitution, for player-facing prose.
PHRASES = {
    "Contents": ("Contenu", "中身"),
    "Empty": ("Vide", "空"),
    "Earth": ("Terre", "土"),
    "Water": ("Eau", "水"),
    "ERYDON Daedalon": ("ERYDON Daedalon", "ERYDON Daedalon"),
    "ERYDON Daedalon Emblem": ("Emblème d’ERYDON Daedalon", "ERYDON Daedalonのエンブレム"),
    "ERYDON now uses native 16x textures. Download the optional 32x and 64x packs from %s or %s.": ("ERYDON utilise désormais des textures natives en 16x. Téléchargez les packs facultatifs en 32x et 64x sur %s ou %s.", "ERYDONは標準で16xテクスチャを使用します。追加の32x・64xパックは%sまたは%sからダウンロードできます。"),
    "Modrinth": ("Modrinth", "Modrinth"),
    "CurseForge": ("CurseForge", "CurseForge"),
    "Open the ERYDON Modrinth page": ("Ouvrir la page ERYDON sur Modrinth", "ModrinthのERYDONページを開く"),
    "Open the ERYDON CurseForge projects page": ("Ouvrir la page des projets ERYDON sur CurseForge", "CurseForgeのERYDONプロジェクトページを開く"),
    "Not enough clear space for that fountain size": ("Espace libre insuffisant pour cette taille de fontaine", "この大きさの噴水を置くスペースが足りません"),
    "Fountain plinth added": ("Socle de fontaine ajouté", "噴水の台座を追加しました"),
    "Debug stick: Raised base lifts the whole fountain by one block.": ("Bâton de débogage : l’option Base surélevée relève toute la fontaine d’un bloc.", "デバッグ棒：「底上げ」で噴水全体を1ブロック高くします。"),
    "When waterlogged, water can surround the lower base.": ("Lorsqu’elle est immergée, l’eau peut entourer la base inférieure.", "水没させると、下部の台座の周囲を水で満たせます。"),
    "fountain fountains fountain plinth tiered fountain water feature": ("fontaine fontaines socle de fontaine fontaine à étages jeu d’eau", "噴水 噴水の台座 多段噴水 水景"),
    "Use to build larger, tiered fountains.": ("Permet de construire de grandes fontaines à plusieurs étages.", "大きな多段式の噴水を作れます。"),
    "Right-click a fountain basin repeatedly with the same plinth to add its base and bowls.": ("Faites plusieurs clics droits sur un bassin de fontaine avec le même socle pour ajouter sa base et ses vasques.", "同じ台座を持って噴水の水盤を繰り返し右クリックすると、土台や鉢を追加できます。"),
    "Fountain bowl added": ("Vasque de fontaine ajoutée", "噴水の鉢を追加しました"),
    "Not enough clear space for that fountain piece": ("Espace libre insuffisant pour cet élément de fontaine", "この噴水の部品を置くスペースが足りません"),
    "This fountain is complete": ("Cette fontaine est complète", "この噴水は完成しています"),
    "Use the same plinth to continue this fountain": ("Utilisez le même socle pour poursuivre cette fontaine", "この噴水に追加するには同じ台座を使用してください"),
    "Light": ("Éclairage", "明るさ"),
    "Low": ("Faible", "弱"),
    "Bright": ("Vif", "強"),
    "Off": ("Éteint", "オフ"),
    "Click a top, underside or wall face for pier, hanging or wall mounting.": ("Cliquez sur une face supérieure, inférieure ou murale pour une pose sur pilier, suspendue ou murale.", "上面・下面・壁面をクリックして、柱上・吊り下げ・壁掛けの取り付け方を選べます。"),
    "Debug stick: Small 0.75m; Large 1.25m. Hanging chain adds 0.5m.": ("Bâton de débogage : Petite 0,75 m ; Grande 1,25 m. La chaîne de suspension ajoute 0,5 m.", "デバッグ棒：小は0.75m、大は1.25m。吊り下げ用の鎖は0.5m加わります。"),
    "Use a lantern underneath a hanging one to extend its chain; sneak to add another lantern.": ("Utilisez une lanterne sous une lanterne suspendue pour allonger sa chaîne ; accroupissez-vous pour ajouter une autre lanterne.", "吊り下げたランタンの下にランタンを使うと鎖が伸びます。スニークしながら使うとランタンを追加します。"),
    "Bright (15) by default. Debug stick Light: Low (10), Bright (15), Off.": ("Vif (15) par défaut. Éclairage avec le bâton de débogage : Faible (10), Vif (15), Éteint.", "標準は強（15）です。デバッグ棒の「明るさ」で弱（10）・強（15）・オフを切り替えられます。"),
    "Not enough clear space for that lantern size.": ("Espace libre insuffisant pour cette taille de lanterne.", "この大きさのランタンを置くスペースが足りません。"),
    "Honed": ("Finition adoucie", "つや消し研磨"),
    "Polished": ("Finition polie", "研磨"),
    "Ganosis": ("Ganosis", "ガノーシス"),
    "Designs": ("Modèles", "デザイン"),
    "Forms": ("Formes", "形状"),
    "Materials and finishes": ("Matériaux et finitions", "素材と仕上げ"),
    "%s: compare all available materials and finishes for this design.": ("%s : comparez tous les matériaux et toutes les finitions disponibles pour ce modèle.", "%s：このデザインで使える素材と仕上げをすべて比較できます。"),
    "Lanterns": ("Lanternes", "ランタン"),
    "Statues": ("Statues", "彫像"),
    "Busts": ("Bustes", "胸像"),
    "Urns": ("Urnes", "壺"),
    "Capitals": ("Chapiteaux", "柱頭"),
    "Corbels": ("Corbeaux", "持ち送り"),
    "Plinths": ("Socles", "台座"),
    "Finials": ("Fleurons", "頂華"),
    "Friezes": ("Frises", "フリーズ"),
    "Panels": ("Panneaux", "パネル"),
    "Benches": ("Bancs", "ベンチ"),
    "Planters": ("Jardinières", "プランター"),
    "Domes": ("Dômes", "ドーム"),
    "Basins": ("Bassins", "水盤"),
    "Fountains": ("Fontaines", "噴水"),
    "Monuments": ("Monuments", "記念碑"),
    "Fountain droplets: Normal (1x). This computer only.": ("Gouttes des fontaines : Normal (1x). Sur cet ordinateur uniquement.", "噴水の水滴：標準（1x）。このコンピューターのみに適用されます。"),
    "Fountain droplets: High (4x). This computer only.": ("Gouttes des fontaines : Élevé (4x). Sur cet ordinateur uniquement.", "噴水の水滴：多め（4x）。このコンピューターのみに適用されます。"),
    "Could not save the fountain particle setting; the previous setting is unchanged.": ("Impossible d’enregistrer le réglage des particules des fontaines ; le réglage précédent est conservé.", "噴水のパーティクル設定を保存できませんでした。以前の設定を維持します。"),
    "Fountain sound: On. This computer only.": ("Son des fontaines : Activé. Sur cet ordinateur uniquement.", "噴水の音：オン。このコンピューターのみに適用されます。"),
    "Fountain sound: Off. This computer only.": ("Son des fontaines : Désactivé. Sur cet ordinateur uniquement.", "噴水の音：オフ。このコンピューターのみに適用されます。"),
    "Orientation": ("Orientation", "向き"),
    "North": ("Nord", "北"),
    "North-east": ("Nord-est", "北東"),
    "East": ("Est", "東"),
    "South-east": ("Sud-est", "南東"),
    "South": ("Sud", "南"),
    "South-west": ("Sud-ouest", "南西"),
    "West": ("Ouest", "西"),
    "North-west": ("Nord-ouest", "北西"),
    "Straight": ("Droit", "正面"),
    "Diagonal (45°)": ("Diagonal (45°)", "斜め（45°）"),
    "Straight (90°)": ("Droit (90°)", "正面（90°）"),
    "Diagonal (135°)": ("Diagonal (135°)", "斜め（135°）"),
    "Size": ("Taille", "大きさ"),
    "Small": ("Petit", "小"),
    "Large": ("Grand", "大"),
    "Not enough clear space for a Large capital (2×2×2 blocks).": ("Espace libre insuffisant pour un grand chapiteau (2×2×2 blocs).", "大きな柱頭を置くスペース（2×2×2ブロック）が足りません。"),
    "Not enough clear space for this dome diameter.": ("Espace libre insuffisant pour ce diamètre de dôme.", "この直径のドームを置くスペースが足りません。"),
    "Not enough clear space for that obelisk size": ("Espace libre insuffisant pour cette taille d’obélisque", "この大きさのオベリスクを置くスペースが足りません"),
    "Not enough clear space for that finial size.": ("Espace libre insuffisant pour cette taille de fleuron.", "この大きさの頂華を置くスペースが足りません。"),
    "Places the twelve Olympian statues in order, then repeats.": ("Place les statues des douze dieux de l’Olympe dans l’ordre, puis recommence.", "オリュンポス十二神の像を順番に設置し、一巡すると最初に戻ります。"),
    "Next: %s": ("Suivant : %s", "次：%s"),
    "Use on a statue's side to change its god; sneak to go backwards.": ("Utilisez-le sur le côté d’une statue pour changer de dieu ; accroupissez-vous pour revenir en arrière.", "像の側面に使うと神を切り替えます。スニークしながら使うと逆順になります。"),
    "Mount": ("Fixation", "取り付け方"),
    "Seated": ("Posée", "柱上"),
    "Wall": ("Murale", "壁掛け"),
    "Hanging": ("Suspendue", "吊り下げ"),
    "Click a top, underside or wall face to mount; debug stick Mount selects Seated, Wall or Hanging.": ("Cliquez sur une face supérieure, inférieure ou murale pour la poser ; Fixation avec le bâton de débogage permet de choisir Posée, Murale ou Suspendue.", "上面・下面・壁面をクリックして取り付けます。デバッグ棒の「取り付け方」で柱上・壁掛け・吊り下げを選べます。"),
    "Not enough clear space for that lantern mount.": ("Espace libre insuffisant pour cette fixation de lanterne.", "この取り付け方でランタンを置くスペースが足りません。"),
    "East / West": ("Est / Ouest", "東／西"),
    "North / South": ("Nord / Sud", "北／南"),
    "Not enough clear space for that obelisk size or position.": ("Espace libre insuffisant pour cette taille ou cette position d’obélisque.", "この大きさ・位置にオベリスクを置くスペースが足りません。"),
}

SEARCH_WORDS = {
    "acanthus": ("acanthe", "アカンサス"), "agate": ("agate", "瑪瑙 メノウ"),
    "aged": ("vieilli vieillie", "風化した 経年"), "alabaster": ("albâtre", "アラバスター 雪花石膏"),
    "animal": ("animal", "動物"), "architecture": ("architecture", "建築"),
    "baroque": ("baroque", "バロック"), "base": ("base", "土台"),
    "basin": ("bassin vasque", "水盤"), "bench": ("banc", "ベンチ"),
    "blue": ("bleu", "青い"), "box": ("bac", "箱"),
    "bracket": ("console", "持ち送り"), "bronze": ("bronze", "青銅"),
    "bust": ("buste", "胸像"), "byzantine": ("byzantin byzantine", "ビザンティン"),
    "capital": ("chapiteau", "柱頭"), "ceiling": ("plafond", "天井"),
    "chain": ("chaîne", "鎖"), "chariot": ("char", "戦車"),
    "classical": ("classique", "古典様式"), "coffer": ("caisson", "格間"),
    "coffered": ("à caissons", "格天井"), "column": ("colonne", "柱"),
    "composite": ("composite", "コンポジット"), "corbel": ("corbeau console", "持ち送り"),
    "corinthian": ("corinthien corinthienne", "コリント式"), "couchant": ("couché", "伏せた"),
    "cross": ("croix", "十字架"), "cupola": ("coupole", "丸屋根"),
    "curved": ("courbe", "湾曲"), "cycle": ("cycle", "順番"),
    "cycling": ("défilement", "切り替え"), "decor": ("décor", "装飾"),
    "decoration": ("décoration", "装飾"), "decorative": ("décoratif décorative", "装飾用"),
    "deity": ("divinité", "神"), "dome": ("dôme", "ドーム"),
    "entablature": ("entablement", "エンタブラチュア"), "feature": ("aménagement", "景観"),
    "finial": ("fleuron", "頂華 フィニアル"), "flower": ("fleur", "花"),
    "flowerpot": ("pot de fleurs", "植木鉢"), "fountain": ("fontaine", "噴水"),
    "frieze": ("frise", "フリーズ"), "furniture": ("mobilier", "家具"),
    "ganosis": ("ganosis", "ガノーシス"), "garden": ("jardin", "庭園"),
    "gas": ("gaz", "ガス"), "burner": ("brûleur", "バーナー"),
    "gazebo": ("kiosque", "あずまや"), "gemstone": ("pierre précieuse", "宝石"),
    "georgian": ("géorgien géorgienne", "ジョージアン"), "glazing": ("vitrage", "ガラス張り"),
    "god": ("dieu", "神"), "goddess": ("déesse", "女神"),
    "gothic": ("gothique", "ゴシック"), "greek": ("grec grecque", "ギリシャ"),
    "guilloche": ("guilloché", "ギヨシェ"), "hanging": ("suspendu suspendue", "吊り下げ"),
    "head": ("tête", "頭部"), "hero": ("héros", "英雄"),
    "honed": ("adouci", "つや消し研磨"), "horse": ("cheval", "馬"),
    "interlace": ("entrelacs", "組紐模様"), "ionic": ("ionique", "イオニア式"),
    "labradorite": ("labradorite", "ラブラドライト"), "lancet": ("lancette", "尖頭アーチ"),
    "lantern": ("lanterne", "ランタン"), "leaf": ("feuille", "葉"),
    "limestone": ("calcaire", "石灰岩"), "lion": ("lion", "ライオン"),
    "marble": ("marbre", "大理石"), "medallion": ("médaillon", "メダリオン"),
    "metal": ("métal", "金属"), "metallic": ("métallique", "金属製"),
    "mirror": ("miroir", "鏡面"), "monument": ("monument", "記念碑"),
    "neoclassical": ("néoclassique", "新古典様式"), "obelisk": ("obélisque", "オベリスク"),
    "olympian": ("olympien Olympe", "オリュンポス"), "onyx": ("onyx", "オニキス"),
    "opal": ("opale", "オパール"), "ornament": ("ornement", "装飾"),
    "ornamental": ("ornemental ornementale", "装飾用"), "ornate": ("orné ornée", "華麗"),
    "palm": ("palme", "ヤシ"), "palmette": ("palmette", "パルメット"),
    "panel": ("panneau", "パネル"), "pavilion": ("pavillon", "東屋"),
    "pavillion": ("pavillon", "東屋"), "pedestal": ("piédestal", "台座"),
    "pier": ("pilier", "柱上"), "pillar": ("pilier", "柱"),
    "pinnacle": ("pinacle", "小尖塔"), "plain": ("simple", "シンプル"),
    "plant": ("plante", "植物"), "planter": ("jardinière", "プランター"),
    "plinth": ("socle", "台座"), "polished": ("poli", "光沢研磨"),
    "portrait": ("portrait", "肖像"), "pot": ("pot", "鉢"),
    "pottery": ("poterie", "陶器"), "procession": ("procession", "行列"),
    "quartzite": ("quartzite", "珪岩"), "quatrefoil": ("quadrilobe", "四葉飾り"),
    "regency": ("Régence anglaise", "リージェンシー"), "relief": ("relief", "浮き彫り"),
    "renaissance": ("Renaissance", "ルネサンス"), "ribbed": ("cannelé cannelée", "リブ付き"),
    "rock": ("roche", "岩石"), "roman": ("romain romaine", "ローマ"),
    "roof": ("toit", "屋根"), "rotunda": ("rotonde", "ロタンダ"),
    "sandstone": ("grès", "砂岩"), "sculpture": ("sculpture", "彫刻"),
    "scroll": ("volute", "渦巻き"), "seat": ("siège", "座席"),
    "seating": ("assise", "腰掛け"), "sequence": ("séquence", "順序"),
    "silver": ("argent", "銀"), "sodalite": ("sodalite", "ソーダライト"),
    "spartan": ("spartiate", "スパルタ"), "statue": ("statue", "像 彫像"),
    "statant": ("debout", "立った"), "stone": ("pierre", "石"),
    "straight": ("droit", "直線"), "tracery": ("remplage", "トレーサリー"),
    "travertine": ("travertin", "トラバーチン"), "tuscan": ("toscan", "トスカナ式"),
    "urn": ("urne", "壺"), "vase": ("vase", "花瓶"),
    "vault": ("voûte", "ヴォールト"), "vessel": ("récipient", "容器"),
    "volute": ("volute", "渦巻き"), "wall": ("mural murale", "壁"),
    "water": ("eau", "水"), "weathered": ("patiné patinée", "風化"),
    "white": ("blanc blanche", "白色"), "black": ("noir noire", "黒色"), "worn": ("usé usée", "摩耗"),
    "rosette": ("rosace rosette", "ロゼット 花飾り"),
}
SEARCH_WORDS.update({name.lower(): labels for name, labels in SUBJECTS.items()})
SEARCH_ALIASES = {
    "apollon": ("Apollon", "アポロン"), "athene": ("Athéna", "アテナ"),
    "bacchus": ("Bacchus", "バッカス"), "ceres": ("Cérès", "ケレス"),
    "diana": ("Diane", "ディアナ"), "dionysos": ("Dionysos", "ディオニュソス"),
    "hephaistos": ("Héphaïstos", "ヘパイストス"), "herakles": ("Héraclès", "ヘラクレス"),
    "hercules": ("Hercule", "ヘルクレス"), "iuppiter": ("Jupiter", "ユピテル"),
    "jove": ("Jupiter", "ユピテル"), "juno": ("Junon", "ユノ"),
    "jupiter": ("Jupiter", "ユピテル"), "liber": ("Liber", "リベル"),
    "liber_pater": ("Liber Pater", "リベル・パテル"), "mars": ("Mars", "マルス"),
    "mercurius": ("Mercure", "メルクリウス"), "mercury": ("Mercure", "メルクリウス"),
    "minerva": ("Minerve", "ミネルウァ"), "neptune": ("Neptune", "ネプトゥヌス"),
    "neptunus": ("Neptune", "ネプトゥヌス"), "phaethon": ("Phaéton", "パエトン"),
    "sol": ("Sol", "ソル"), "venus": ("Vénus", "ウェヌス"),
    "vesta": ("Vesta", "ウェスタ"), "vulcan": ("Vulcain", "ウルカヌス"),
    "vulcanus": ("Vulcain", "ウルカヌス"),
}
# Named designs and invented stones retain searchable Latin spellings.
PROPER_SEARCH_WORDS = frozenset(
    name.lower() for name in MATERIALS
) | frozenset({
    "amphora", "anthophoros", "astragalos", "balanos", "bathron", "daedalon",
    "deadalon", "diota", "exedra", "hedra", "kalyx", "kion", "konche", "krene",
    "kylix", "kynara", "lekythos", "louterion", "monopteros", "obeliskos", "pege",
    "pelike", "phlox", "pithos", "promachos", "rhabdos", "salpinx", "sphaira",
    "stamnos", "stephanos", "strobilos", "triphyllon",
})


def translated_design(name: str, language_index: int) -> str:
    return DESIGNS[name][language_index]


def translated_item(value: str, language_index: int) -> str:
    material, separator, design = value.partition(" ")
    if not separator or material not in MATERIALS:
        raise ValueError(f"Untranslated Daedalon item: {value}")
    aged = design.startswith("Aged ")
    if aged:
        design = design.removeprefix("Aged ")
    label = translated_design(design, language_index)
    material_label = MATERIALS[material][language_index]
    if language_index == 1:
        return f"{'風化した' if aged else ''}{material_label}の{label}"
    if material in ("White", "Black"):
        adjective = {"White": ("blanc", "blanche"), "Black": ("noir", "noire")}[material]
        return f"{label} {adjective[DESIGNS[design][2] == 'f']}"
    return f"{label} en {material_label}{' vieilli' if aged else ''}"


def translated_search(tag: str, language_index: int) -> str:
    if tag in SEARCH_ALIASES:
        return SEARCH_ALIASES[tag][language_index]
    words = []
    for word in tag.split("_"):
        if word in SEARCH_WORDS:
            words.append(SEARCH_WORDS[word][language_index])
        elif word in PROPER_SEARCH_WORDS:
            words.append(word)
        else:
            raise ValueError(f"Untranslated Daedalon search word: {word}")
    return " ".join(words)


def translate_languages(english: dict[str, str]) -> dict[str, dict[str, str]]:
    """Fail on unknown text so future generated resources cannot hide gaps."""
    result = {}
    for index, locale in enumerate(LOCALES):
        entries = {}
        for key, value in english.items():
            if key in CLIENT_SETTINGS_TRANSLATIONS[locale]:
                translated = CLIENT_SETTINGS_TRANSLATIONS[locale][key]
            elif key.startswith("search.daedalon.tag."):
                translated = translated_search(key.removeprefix("search.daedalon.tag."), index)
            elif value in PHRASES:
                translated = PHRASES[value][index]
            elif key.startswith(("block.daedalon.", "item.daedalon.")):
                translated = translated_item(value, index)
            elif key.startswith("catalogue.daedalon.design."):
                translated = translated_design(value, index)
            elif key.startswith("catalogue.daedalon.material."):
                translated = MATERIALS[value][index]
                if index == 0:
                    translated = translated[0].upper() + translated[1:]
            else:
                raise ValueError(f"Untranslated Daedalon UI entry: {key} = {value}")
            entries[key] = translated
        result[locale] = entries
    return result
