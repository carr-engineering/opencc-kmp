import UIKit
import ConsumerKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let report = ConsumerExample.shared.run()
        UserDefaults.standard.set(report, forKey: "OpenCCFixtureReport")
        UserDefaults.standard.synchronize()
        let value = try! JSONSerialization.jsonObject(with: Data(report.utf8)) as! [String: Any]
        let controller = UIViewController()
        controller.view.backgroundColor = .systemBackground
        let label = UILabel(frame: CGRect(x: 20, y: 100, width: 320, height: 80))
        label.text = value["output"] as? String ?? "Fixture failed"
        controller.view.addSubview(label)
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = controller
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
