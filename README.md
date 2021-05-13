# DHT

**Le but de ce projet est de concevoir et implémenter une DHT en dessus de PeerSim.**

> Pour lancer le projet, il suffit d'executer :
> ```shell
> ./gradlew run
> ```
> Ce ci va compiler tout le code et lancer le simulateur

# Étape 1 : Join/Leave
Dans un premier temps, la DHT est un simple anneau où chaque noeud 
connait uniquement ses voisins immédiats. Chaque noeud a un identifiant 
(aléatoire), les noeuds sont rangés dans l'anneau en fonction de leur 
identifiant. Pour les identifiants on utilise UUID qui va fournir un ID 
unique et aléatoire à chaque noeud. 
Pour faire simple un noeud initial va se réveiller, en suite les autres 
noeuds du Network vont se réveiller un par un et vont trouver leur place 
dans le cercle en parcourant de gauche à droite (gauche = plus petit, 
droite = plus grand). 


## Couche transport
Dans la couche transport nous utilisons un recepteur d'évenement qui va 
rediriger les différents protocoles dans les bonnes méthodes. 
C'est aussi grâce à cette couche que nos noeuds peuvent communiquer.

## Les différents protocoles
Pour clarifier cela nous utilisons plusieurs protocoles qui héritent tous 
de de l'interface Packet :

- **DiscoveryPacket**
> Ce protocole est utilisé lorsqu'un noeud sort de son sommeil. Celui-ci va 
> se propager dans cercle jusqu'à trouver l'emplacement de son émetteur.
- **WelcomePacket**
> Le WelcomePacket est envoyé à un nouveau entrant dans le cercle pour lui 
> présenter ses voisins.
- **SwitchingNeighborPacket** 
> Et enfin ce protocole est utilisé pour avertir un noeud qu'il va changer de 
> voisin (que ce soit pour une entré de noeud ou pour le départ d'un d'entre eux). 

Voici deux dessins pour rendre ca plus visuel. Le premier montre comment un 
noeud rentre dans le network alors que le deuxième montre comment il en sort : 

![Entree dans le network](img/entree.png)

![Sortie dans le network](img/sortie.png)

# Étape 2 : Send/Receive
Pour cette partie nous avons créer un nouveau protocole : 

- **RoutablePacket**

Celui-ci est divisé en 2 parties  : 

- MessagePacket

- UndeliverableRoutablePacket

Le premier est le message qui sera délivré à la target, celui-ci parcourt 
le cercle à la même manière qu'un DiscoveryPacket. 
Si la target n'est pas dans le network alors le noeud qui detecte cette 
'anomalie' va retourner un UndeliverableRoutablePacket au noeud sender.

![Envoie d'un messsage](img/envoieMessage.png)

![Erreur sur envoie d'un message](img/errMessage.png)

# Les outils utilisés 
Dans un premier temps nous utilisions le Makefile pour compiler et tester 
notre code mais nous nous sommes vite rendu compte que cela n'était pas 
pratique et que le débug était impossible. Nous avons donc utiliser 
**Gradle** simplifier cela. 

Toujours dans une optique d'éfficacité nos avons utiliser **Log4J** pour 
avoir des logs propres et utilisables. 

# Résultats avec les logs 

Les logs sont découpés en plusieurs actions pour faciliter leur lecture. 

![Logs insertion noeud dans le network](img/log1.png)

Ici on voit l'initialisation d'un network avec un noeud puis l'ajout de 
deux noeuds dans ce même network (Action 0 et Action 1).

![Logs network final](img/log2.png)

Sur l'action 9 que l'on voit au dessus le final ring est représenté. 
On voit que tous les noeuds sont dans le bon ordre d'UUID. 
**ATTENTION on ne commence pas forcément avec le plus petit, pas grave c'est un cercle :)** .

![Logs suppression d'un noeud du network](img/log3.png)

Ici on enlève le noeud 0 du ring. 

![Logs envoie d'un message entre 2 noeuds](img/log4.png)

Et enfin on envoie un message du noeud 7 au noeud 3, on voit que l'ordre de 
routing du packet est bon. On remarque aussi que la supression à bien fonctionner car 
initialement le packet aurait du passer par le noeud 0.
