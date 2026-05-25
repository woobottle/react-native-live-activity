module.exports = {
  dependency: {
    platforms: {
      ios: {},
      android: {
        packageImportPath: 'import com.woobottle.liveactivity.LiveActivityPackage;',
        packageInstance: 'new LiveActivityPackage()',
      },
    },
  },
}
