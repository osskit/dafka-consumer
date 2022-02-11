module.exports = {
    preset: 'ts-jest',
    testEnvironment: 'node',
    rootDir: '.',
    testMatch: ['<rootDir>/specs/**.spec.ts'],
    globals: {
        'ts-jest': {
            tsConfig: {
                strictPropertyInitialization: false,
                noUnusedLocals: false,
            },
        },
    },
};
