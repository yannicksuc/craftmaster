# Finitions (v4, chantier G)

Trois améliorations demandées pour le direct :

## Rideaux qui glissent
Le tissu se déplace **en continu** au lieu de changer colonne par colonne : les deux moitiés partent du milieu,
vont se ranger sur les côtés en plis serrés et profonds, le bas suit le haut avec un léger retard, ondule pendant
le mouvement et se balance une fois à l'arrêt. Les 16 couleurs ont des plis plus marqués (creux, flanc, crête),
l'ourlet reste sur le bloc du bas. Redstone, télécommande, voix, commandes et décors : rien ne change, et tous les
joueurs voient le même mouvement. Détails : [Rideaux](Rideaux.md).

## Éditeur de poses
Un aperçu 3D du tableau dans l'écran : on **clique une partie du corps** pour la choisir, on la **tire** pour la
tourner, la molette ou les flèches font des pas de 1°, Ctrl+Z / Ctrl+Y annulent et refont, M fait le miroir
gauche ↔ droite, C copie un bras ou une jambe de l'autre côté, R remet droit, H cache. Le tableau dans le monde
montre toujours la pose en direct. L'écran de réglages du tableau a des infobulles partout et Entrée enregistre.
Tous les gestes : [Tableaux](Tableaux.md#poses).

## Skins HD et capes
Une adresse de skin peut donner une image HD : 128×128, 256×256, 512×512, 1024×1024 (tout multiple de 64 jusqu'à
1024, et les anciens formats 2:1). Les bras fins sont reconnus à toutes les tailles. Un tableau qui porte le skin
du joueur montre aussi sa **cape**. Détails : [Tableaux](Tableaux.md).

## Pour tester
[Tests](Tests.md) : tests unitaires `CurtainClothTest`, `PoseEditingTest`, `SkinFormatTest`, et les clics / touches
simulés dans les écrans en dev (`run/dev-input.txt`).
