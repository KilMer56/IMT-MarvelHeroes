const csv = require('csv-parser');
const fs = require('fs');

const { Client } = require('@elastic/elasticsearch')
const esClient = new Client({ node: 'http://localhost:9200' })
const heroesIndexName = 'heroes'

// Supprime l'indice existant
esClient.indices.delete({ index: heroesIndexName }, (err, resp) => {
    if (err) console.trace(err.message);
});

// Création de l'indice
esClient.indices.create({ index: heroesIndexName }, (err, resp) => {
    if (err) console.trace(err.message);
});

let heroes = [];
fs
    .createReadStream('./all-heroes.csv')
    .pipe(csv())
    // Pour chaque ligne on créé un document JSON pour l'acteur correspondant
    .on('data', data => {
        heroes.push(data);
    })
    // A la fin on créé l'ensemble des acteurs dans ElasticSearch
    .on('end', () => {
        while (heroes.length) {
            esClient.bulk(createBulkInsertQuery(heroes.splice(0, 20000)), (err, resp) => {
                if (err) console.trace(err.message);
                else console.log(`Inserted ${resp.body.items.length} heroes`);
                esClient.close();
            });
        }
    });

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