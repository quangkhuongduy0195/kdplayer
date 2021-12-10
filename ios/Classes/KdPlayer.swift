import MediaPlayer
import AVKit

class KdPlayer: NSObject {
    private var player: AVPlayer!
    private var playerItem: AVPlayerItem!
    private var metadata: Array<String>!
    var defaultArtwork: UIImage?
    var metadataArtwork: UIImage?
    var audioAsset:AVAsset!
    var audioDurationSeconds : Double = 0.0
    
    func setMediaItem(_ streamTitle: String?, _ streamUrl: String) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [MPMediaItemPropertyTitle: streamTitle ?? "", ]
        defaultArtwork = nil
        metadataArtwork = nil
        playerItem = AVPlayerItem(url: URL(string: streamUrl)!)
        playerItem.addObserver(self, forKeyPath: "timedMetadata", options: [.new], context: nil)
        
        if (player == nil) {
            // Create an AVPlayer.
            player = AVPlayer()
            player.addObserver(self, forKeyPath: #keyPath(AVPlayer.timeControlStatus), options: [.new], context: nil)
            player.addObserver(self, forKeyPath: #keyPath(AVPlayer.currentItem), options: [.new], context: nil)
            runInBackground()
            NotificationCenter.default.addObserver(self, selector: #selector(playerDidFinishPlaying(_:)), name: .AVPlayerItemDidPlayToEndTime, object: nil)
        }
        player.replaceCurrentItem(with: playerItem)
        getMetadata(streamUrl: streamUrl, streamTitle: streamTitle)
    }
    
    @objc func playerDidFinishPlaying(_ note: NSNotification) {
        player.currentItem?.seek(to: CMTime(value: 0, timescale: 1), completionHandler: { val in
        })
    }
    
    func getMetadata(streamUrl : String, streamTitle : String?){
        // metadata handler.
        var title:String = ""
        var artistImage : UIImage!
        audioAsset = AVURLAsset(url: URL(string: streamUrl)!, options: nil)
        let audioDuration: CMTime   = audioAsset.duration
        audioDurationSeconds    = audioDuration.seconds
        guard !(audioDurationSeconds.isNaN || audioDurationSeconds.isInfinite) else {
            audioDurationSeconds = 0.0
            return
        }
        let metadataList = audioAsset.metadata
        for item in metadataList {
            
            guard let key = item.commonKey?.rawValue, let value = item.value else{
                continue
            }

            switch key {
            case "title": title     = (value as? String)!
            case "artwork" where value is Data : artistImage = UIImage(data: value as! Data)!
            default:
                continue
            }
        }
        metadata = title.components(separatedBy: " - ")
    
        
        // Send metadata to client
        NotificationCenter.default.post(name: NSNotification.Name(rawValue: "metadata"), object: nil, userInfo: ["metadata": metadata!])
        NotificationCenter.default.post(name: NSNotification.Name(rawValue: "duration"), object: nil, userInfo: ["duration": audioDurationSeconds * 1000])
        
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [MPMediaItemPropertyTitle: streamTitle ?? title,
                                               MPMediaItemPropertyPlaybackDuration : audioDurationSeconds,
                                               MPNowPlayingInfoPropertyPlaybackRate: 0,
        ]
        setArtwork(artistImage)

        
        NotificationCenter.default.post(name: NSNotification.Name(rawValue: "current"), object: nil, userInfo: ["current": 0.0])
        MPNowPlayingInfoCenter.default().nowPlayingInfo![MPNowPlayingInfoPropertyPlaybackRate] = 1
        self.player.addPeriodicTimeObserver(forInterval: CMTimeMakeWithSeconds(1, preferredTimescale: 2), queue: DispatchQueue.main) { (cmtime) in
            if self.player!.currentItem?.status == AVPlayerItem.Status.readyToPlay {
                var time:Double = Double(CMTimeGetSeconds(self.player.currentTime()))
                print(time)
                MPNowPlayingInfoCenter.default().nowPlayingInfo![MPNowPlayingInfoPropertyElapsedPlaybackTime] = CMTimeGetSeconds(self.player.currentTime())
                if(time > Double(self.audioDurationSeconds)) {
                    time = Double(self.audioDurationSeconds)
                }
                NotificationCenter.default.post(name: NSNotification.Name(rawValue: "current"), object: nil, userInfo: ["current": time * 1000])
            }
        }
    }
    
    func setArtwork(_ image: UIImage?) {
        guard let image = image else { return }
        
        let artwork = MPMediaItemArtwork(boundsSize: image.size) { (size) -> UIImage in image }
        MPNowPlayingInfoCenter.default().nowPlayingInfo?.updateValue(artwork, forKey: MPMediaItemPropertyArtwork)
    }
    
    func play() {
        player.play()
    }
    
    func pause() {
        player.pause()
    }
    
    func stop() {
        player.pause()
    }
    var isSeekTo = false;
    func setSeekTo(timer: Double) {
        if(isSeekTo) {
            return
        }
        self.player.pause()
        isSeekTo = true;
        print("seek: \(timer)")
        let timerSet = timer/1000
        player.currentItem?.seek(to: CMTime(value: Int64(timerSet), timescale: 1), completionHandler: { val in
            print("completionHandler: \(timerSet)")
            self.isSeekTo = false
            self.player.play()
        })
    }

    
    func runInBackground() {
        try? AVAudioSession.sharedInstance().setActive(true)
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        
        // Control buttons on the lock screen.
        UIApplication.shared.beginReceivingRemoteControlEvents()
        let commandCenter = MPRemoteCommandCenter.shared()
        
        // Play button.
        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { [weak self] (event) -> MPRemoteCommandHandlerStatus in
            self?.play()
            return .success
        }
        
        // Pause button.
        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { [weak self] (event) -> MPRemoteCommandHandlerStatus in
            self?.pause()
            return .success
        }
    }
    
    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey: Any]?, context: UnsafeMutableRawPointer?) {
        print("---------------\(String(describing: keyPath))")
        guard let observedKeyPath = keyPath, object is AVPlayer, observedKeyPath == #keyPath(AVPlayer.timeControlStatus) else {
            return
        }
        
        if let statusAsNumber = change?[NSKeyValueChangeKey.newKey] as? NSNumber {
            let status = AVPlayer.TimeControlStatus(rawValue: statusAsNumber.intValue)
            if status == .paused {
                NotificationCenter.default.post(name: NSNotification.Name(rawValue: "state"), object: nil, userInfo: ["state": false])
            } else if status == .waitingToPlayAtSpecifiedRate {
                NotificationCenter.default.post(name: NSNotification.Name(rawValue: "state"), object: nil, userInfo: ["state": true])
            }
        }
    }
    
    func downloadImage(_ value: String) -> UIImage? {
        guard let url = URL(string: value) else { return nil }
        
        var result: UIImage?
        let semaphore = DispatchSemaphore(value: 0)
        
        let task = URLSession.shared.dataTask(with: url) { (data, response, error) in
            if let data = data, error == nil {
                result = UIImage(data: data)
            }
            semaphore.signal()
        }
        task.resume()
        semaphore.wait()
        return result
    }
}
