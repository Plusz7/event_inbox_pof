db = db.getSiblingDB('eventpof');

db.createCollection('outbox_events');
db.createCollection('processed_events');

db.outbox_events.createIndex({ eventKey: 1 }, { unique: true });
db.outbox_events.createIndex({ status: 1, createdAt: 1 });

db.processed_events.createIndex({ eventKey: 1 }, { unique: true });
db.processed_events.createIndex({ status: 1 });
db.processed_events.createIndex({ processedAt: 1 });

print('MongoDB initialized: eventpof database and indexes created');
