var mongodb = require("mongodb");
var csv = require("csv-parser");
var fs = require("fs");

var MongoClient = mongodb.MongoClient;
var mongoUrl = "mongodb://localhost:27017";
const dbName = "marvel";
const collectionName = "heroes";

const insertHeroes = (db, callback) => {
    const collection = db.collection(collectionName);

    const heroes = [];
    fs.createReadStream('./all-heroes.csv')
        .pipe(csv())
        // Pour chaque ligne on créé un document JSON pour l'acteur correspondant
        .on('data', data => {
            const hero = {
                id: data.id,
                name: data.name,
                imageUrl: data.imageUrl,
                backgroundImageUrl: data.backgroundImageUrl,
                externalLink: data.externalLink,
                description: data.description,
                teams: data.teams,
                powers: data.powers.length ? data.powers.split(',') : [],
                partners: data.partners,
                creators: data.creators,
                appearance: {
                    gender: data.gender,
                    type: data.type,
                    race: data["race"],
                    height: data.height,
                    weight: data.weight,
                    eyeColor: data.eyeColor,
                    hairColor: data.hairColor
                },
                identity: {
                    secretIdentities: data.secretIdentities,
                    birthPlace: data.birthPlace,
                    occupation: data.occupation,
                    aliases: data.aliases,
                    alignment: data.alignment,
                    firstAppearance: data.firstAppearance,
                    yearAppearance: data.yearAppearance,
                    universe: data.universe
                },
                skills: {
                    intelligence: data.intelligence.length ? parseInt(data.intelligence, 10) : 0,
                    strength: data.strength.length ? parseInt(data.strength, 10) : 0,
                    speed: data.speed.length ? parseInt(data.speed, 10) : 0,
                    durability: data.durability.length ? parseInt(data.durability, 10) : 0,
                    combat: data.combat.length ? parseInt(data.combat, 10) : 0,
                    power: data.power.length ? parseInt(data.power, 10) : 0
                }
            };

            heroes.push(hero);
        })
        // A la fin on créé l'ensemble des héros dans MongoDB
        .on('end', () => {
            collection.insertMany(heroes, (err, result) => {
                callback(result);
            });
        });
}

MongoClient.connect(mongoUrl, async (err, client) => {
    if (err) {
        console.error(err);
        throw err;
    }

    const db = client.db(dbName);

    db.dropCollection(collectionName);

    insertHeroes(db, result => {
        console.log(`${result.insertedCount} heroes inserted`);
        client.close();
    });
});