module.exports = {
  preset: 'react-native',
  moduleNameMapper: {
    // The library is symlinked (file:..) and would otherwise resolve the repo
    // root's own react-native copy, giving two RN instances (only the example's
    // is patched by the preset). Force a single instance.
    '^react-native$': '<rootDir>/node_modules/react-native',
  },
};
