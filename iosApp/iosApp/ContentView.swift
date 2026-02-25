import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        print("🔵 Creating MainViewController...")
        let viewController = MainViewControllerKt.MainViewController()
        viewController.view.backgroundColor = .white
        print("🔵 MainViewController created successfully")
        
        // Добавляем задержку для отладки
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            print("🔵 View bounds: \(viewController.view.bounds)")
            print("🔵 View subviews count: \(viewController.view.subviews.count)")
        }
        
        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        print("🔵 Updating view controller")
    }
}

struct ContentView: View {
    var body: some View {
        ZStack {
            ComposeView()
                .ignoresSafeArea(.all, edges: .all)
        }
        .onAppear {
            print("🔵 ContentView appeared")
        }
    }
}



