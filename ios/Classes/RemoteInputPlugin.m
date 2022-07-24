#import "RemoteInputPlugin.h"
#if __has_include(<remote_input/remote_input-Swift.h>)
#import <remote_input/remote_input-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "remote_input-Swift.h"
#endif

@implementation RemoteInputPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftRemoteInputPlugin registerWithRegistrar:registrar];
}
@end
