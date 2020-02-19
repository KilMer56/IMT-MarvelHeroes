const csv = require('csv-parser');
const fs = require('fs');

const { Client } = require('@elastic/elasticsearch')
const esClient = new Client({ node: 'http://localhost:9200' })
const heroesIndexName = 'heroes'

async function run() {

    await checkIndices();

    // Ajout completion suggest
    await esClient.indices.put_mapping({
        index: heroesIndexName,
        body: {
            properties: {
                suggest: {
                    type: 'completion'
                }
            }
        }
    });

    let heroes = [];
    fs
        .createReadStream('./all-heroes.csv')
        .pipe(csv())
        // Pour chaque ligne on créé un document JSON pour le héro correspondant
        .on('data', data => {
            heroes.push({
                'id': data.id,
                'name': data.name,
                'description': data.description,
                'imageUrl': data.imageUrl,
                'secretIdentities': data.secretIdentities,
                'aliases': data.aliases,
                'partners': data.partners,
                'universe': data.universe,
                'gender': data.gender,
                'suggest': [
                    {
                        "input": data.name,
                        "weight": 10
                    },
                    {
                        "input": data.aliases,
                        "weight": 5
                    },
                    {
                        "input": data.secretIdentities,
                        "weight": 5
                    },
                ]
            });
        })
        // A la fin on créé l'ensemble des héros dans ElasticSearch
        .on('end', () => {
            while (heroes.length) {
                esClient.bulk(createBulkInsertQuery(heroes.splice(0, 20000)), (err, resp) => {
                    if (err) console.trace(err.message);
                    else console.log(`Inserted ${resp.body.items.length} heroes`);
                    esClient.close();
                });
            }
        });
}

// Fonction utilitaire permettant de formatter les données pour l'insertion "bulk" dans elastic
function createBulkInsertQuery(heroes) {
    const body = heroes.reduce((acc, hero) => {
        let id = hero.id;
        delete (hero.id);
        acc.push({ index: { _index: 'heroes', _type: '_doc', _id: id } })
        acc.push(hero);
        return acc
    }, []);

    return { body };
}

// Check indices
async function checkIndices() {
    const { body: exist } = await esClient.indices.exists({ index: heroesIndexName });

    if (exist) {
        await esClient.indices.delete({ index: heroesIndexName });
    }

    await esClient.indices.create({ index: heroesIndexName });
}

run().catch(console.error());