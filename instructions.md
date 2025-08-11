we are planning a game of hide and seek where the seekers can draw challenge cards which grant seekers hints about the hiders in exchange for activities like find x stop signs. a challenge card cannot be drawn twice by the same team. right now we are using paper card deck. We want to implement the following features using a react native app, and a simple backend api for hider location clue generation, there should be a separate folder for backend and frontend, and all in typescript:

- Simulate drawing cards (randomized, can be curses or challenges, cannot be drawn twice in a round) (a json file containing the challenges and curses with their token count is included)
- Tracking token balance, each challenge grants them tokens (they can veto the challenge which incurs a 5 minute no-challenge penalty or complete the challenge)
-Menu with clues to find the hiders: can be bought with tokens

the clues must be based on hider locations: we are focused on playing at the university of british columbia, and the hiders will run the app which will share the location to a backend. the backend will then take their coordinates and use a combination of logic and ai to generate clues to the seekers which can be bought as above. e.g. "this building is known for its physics research" etc.

here are the tentative instructions:

UBCeek: Hide and Seek across UBC

Game:
Teams compete to hide the longest amount of time.

Teams start out as the Hiders, and get a 10 minutes headstart to hide somewhere in the game area. One team starts as the Seekers, and will try to track down and tag all the Hider teams.

The Hiders may move around (no running) within certain limitations (define this) during a round, but may not take transit. The Hiders may not hide in washrooms or areas that are restricted/require special access (keycard or sneaking in places).
Once every team is found, team positions rotate and a different team becomes the new Seekers while the former Seekers become Hiders.

Challenges:
To earn tokens, the Seekers may draw Challenge Cards. If a team completes the challenge indicated on the card, they earn a specified amount of Tokens. 
Teams may choose to veto a Challenge Card for any reason, which skips the challenge, rewards no tokens, and incurs a 5-minute no-challenge penalty.
Only one challenge may be active at a time. Teams may not attempt any challenge they have previously completed.

Hider Clues:
- The Seekers can use their Tokens to buy clues on the Hiders’ locations. 
- Any specific clue can be chosen, but it will be assigned to a random non-found Hider team. 
- The price of a clue is determined by the clue’s strength. 
The possible clues include:
Exact current location on map (10)
Selfie of whole team at arm’s length including surroundings
Picture of nearest building (or interior of current building)
Picture of tallest building you can see
Relative direction to Seekers
Distance from Seekers (in Google Maps walking time maybe?)
Are you inside or outside a building?
Name of closest:
named street
landmark
library
museum/gallery
parking lot

Seekers’ Curses:
Seekers may also spend 10 tokens to Curse a specific team. The curse that is pulled is random. Possible curses:
Freeze hiders for 2-3 minutes
Lockdown: If Hiders are in a building, they are locked in for 10 minutes
Hiders must stay outside for ten minutes
Must send a message of your choice to the Hiders, psychological damage
Caddy curse: one team member holds everyone’s stuff for 20 minutes
Blindfold: one team member is blindfolded for 10 minutes
Hiders must visit the fountain before they are found, or else they are penalized 5 minutes
Briefing notes:
Date: Sunday Aug 24
Teams:
Ayden / Brendan
Leo / Simon
Ryan / Kevin
Tom / Nick

Minimize time spent on breaks between rounds, ideally to 15 mins max (excluding lunch). This means quick washroom breaks and water refills.

Make sure to shuffle the challenge cards before passing them on to the next team.

When drawing cards, draw from the front and discard to the back. 

Read the challenge cards carefully, the wording is important!
Filming notes (if filming):
Film every card draw and every challenge until completion.
Film in landscape mode.
App notes (u759/hideAndSeek):
Challenges/curses and randomization
Tracking token balance (veto/completed a challenge)
Menu with clues, can buy them (need hider info)
