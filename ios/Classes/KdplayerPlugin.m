#import "KdplayerPlugin.h"
#if __has_include(<kdplayer/kdplayer-Swift.h>)
#import <kdplayer/kdplayer-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "kdplayer-Swift.h"
#endif

@implementation KdplayerPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftKdplayerPlugin registerWithRegistrar:registrar];
}
@end
