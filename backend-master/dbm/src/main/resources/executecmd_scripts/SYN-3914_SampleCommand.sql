{
    update: 'entityDefinition',
    updates: [
        { 
            q: { apiName: 'account' },
            u: { $set: { seeded: true } },
        }
    ]
}