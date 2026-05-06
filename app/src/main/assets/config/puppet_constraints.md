# Contraintes physiques de la marionnette

Ce fichier définit ce que la marionnette PEUT et NE PEUT PAS faire.
Il est lu à chaque génération et injecté dans le prompt de l'Orchestrateur.
**Ne pas modifier sans raison -- ces valeurs garantissent un rendu cohérent.**

## Mouvements de tête

- Rotation gauche/droite (gazeX) : -0.8 à +0.8 — ne pas dépasser
- Regard haut/bas (gazeY) : -0.7 (bas) à +0.4 (haut)
- Inclinaison (roll) : -0.35 à +0.35 rad
- Vitesse de transition : minimum 0.3s entre deux positions extrêmes

## Bouche

- Ouverture pilotée automatiquement par l'amplitude audio — ne pas forcer
- L'Orchestrateur ne doit PAS générer d'actions setOpen — c'est le lip sync qui s'en charge

## Déplacements

- La marionnette peut se déplacer latéralement sur l'axe X : -1.5 à +1.5 (unités scène)
- Elle peut entrer depuis la gauche (X: -2.0) ou la droite (X: +2.0)
- Elle peut sortir du cadre de la même façon
- Vitesse de déplacement max : 1.0 unité/seconde

## Rythme général

- Minimum 2 changements de position par 30 secondes — la marionnette ne doit jamais rester figée
- Maximum 1 changement de position par 0.8 secondes — pas de mouvements frénétiques
- Les pauses (marionnette immobile) ne doivent pas dépasser 4 secondes consécutives

