package org.schabi.newpipe;

import static org.schabi.newpipe.util.ListHelper.getUrlAndNonTorrentStreams;
import static org.schabi.newpipe.util.external_communication.ShareUtils.copyToClipboard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.views.NyWebView;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class YoutubeFragment extends BaseFragment {
    private NyWebView webView;
    private String mediaLink = null;
    private static final int REQUEST_CODE = 123;
    private String extractLink = null;
    private boolean inList = false;
    @Nullable
    private StreamInfo currentInfo = null;

    private String VID=null;
    private String extractId = null;
    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {
        setTitle("NyPipe");

        View root = inflater.inflate(R.layout.fragment_youtube, container, false);
        webView = root.findViewById(R.id.web_view);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.canGoBack();
        webView.setFocusableInTouchMode(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {

                String url = String.valueOf(request.getUrl());

                //Method 1
                if (url.contains("//m.youtube.com/watch?")) {
                    Log.e(TAG, "url= " + url);
                    //Video Id
                    VID = url.substring(url.indexOf("v=") + 2).split("&")[0];
                    if (VID != null) {
                        mediaLink = "https://m.youtube.com/" + VID;
                        extractId = VID;
                        handleExtractId();
                    }
                    return super.shouldInterceptRequest(view, request);
                } //Method 2
                else if (!url.contains("stats") && url.contains("video_id=") && !url.contains("pltype=adhost")) {
                    // Log.e(TAG, "video link = " + url);
                    VID = url.substring(url.indexOf("video_id=") + 9).split("&")[0];
                    if (VID != null) {
                        mediaLink = "https://youtu.be/" + VID;
                        extractId = VID;
                        handleExtractId();
                    }
                    return new WebResourceResponse("text/plain", "utf-8", null);
                }

                // Method 3
                if (isAdsVideo(url))
                    return new WebResourceResponse("text/plain", "utf-8", null);

                if (url.contains("list=")) {
                    inList = true;
                }

                if (!toSkip(url)) {
                    Handler handler = new Handler(requireActivity().getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            String load = webView.getUrl();
                            //  Log.e(TAG, "url = " + url);
                            // Log.e(TAG, "view.getUrl() = " + load);
                            VID = getYoutubeId(load);
                            if (VID != null) VID = VID.replace("shorts/", "");
                            if (VID != null && !Objects.equals(extractId, VID)) {
                                extractId = VID;
                                mediaLink = "https://youtu.be/" + extractId;
                                handleExtractId();
                            }
                        }
                    });
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN && webView.canGoBack()) {
                    // Navigate back by the top of playlist
                  //  fab.setVisibility(View.GONE);
                    int steps = webView.copyBackForwardList().getSize();
                    if (inList && steps > 2) {
                        webView.goBackOrForward(-steps + 2);
                        inList = !inList; //TO the top of the list, will be become true once we play another video in the list
                    } else webView.goBackOrForward(-steps + 1);
                    return true;  // Consumes the back button press
                }
                return false;
            }
        });

      //        fab.setVisibility(View.GONE);
      ///  fab.setOnClickListener(v -> {
    //        getVideoLink(mediaLink,REQUEST_OPTION);
     //   });

        webView.loadUrl("https://m.youtube.com/");

        return root;
    }

    private boolean isAdsVideo(String url) {
        return url.contains("pagead") || url.contains("adview") || url.contains("ad_status")
                || url.contains("adhost") || url.contains("shop") || url.contains("ad.js");
    }

    private boolean toSkip(String url) {
        return url.contains("log_event") || url.contains("youtubei") || url.contains("log?") || url.contains("adhost") || url.contains("i.ytimg.com")
                || url.contains("yt3") || url.contains("/js/") || url.contains("simgad") || url.contains("ptracking") || url.contains("generate") || url.contains("static")
                || url.contains("googleapis") || url.contains("/s/") || url.contains("stats");
    }


    public void handleExtractId() {
      //  copyToClipboard(requireActivity(), extractLink);
        getInfoHere(mediaLink);
    }
    private static final String youtubeRegex = "^((?:https?:)?\\/\\/)?((?:www|m)\\.)?((?:youtube\\.com|youtu.be))(\\/(?:[\\w\\-]+\\?v=|embed\\/|v\\/|shorts\\/)?)([\\w\\-\\/]+)(\\S+)?$";

    public static String getYoutubeId(String url) {
        Pattern pattern = Pattern.compile(youtubeRegex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String id = matcher.group(5); // Group 5 contains the video ID
            if (url.contains("shorts")) id = "shorts/" + id;
            return id;
        }
        return null;
    }
    @Override
    public void onResume() {
        super.onResume();
        setTitle("NyPipe");
    }


    public static Single<StreamInfo> getStreamInfo(final int serviceId, final String url,
                                                   final boolean forceLoad) {
        return Single.fromCallable(() -> StreamInfo.getInfo(NewPipe.getService(serviceId), url));
    }

    private void getInfoHere(String mediaLink) {
        extractLink = null;
        final CompositeDisposable disposables = new CompositeDisposable();
        disposables.add(getStreamInfo(0, mediaLink, true)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    currentInfo = info;
                    NavigationHelper.openVideoDetailFragment(getActivity(),getFM(),0,info.getUrl(),info.getName(),null,false);
                }, throwable -> {
                }));
    }

    public static String REQUEST_ACTION = "request_action";

    public static int REQUEST_NO_RETURN =-1;//default for no return
    public static int REQUEST_INFO = 0;  //reserved for future use
    public static int REQUEST_OPTION = 1;
    public static int REQUEST_DEFAULT = 2;

    public static String VIDEO_LINK = "video_link";
    public static String VIDEO_NAME = "video_name";
    public static String VIDEO_DURATION = "video_durationn";
    public static String VIDEO_SIZE = "video_size";
    public static String VIDEO_TYPE = "video_type";


    private void getInfoFromResult(String mediaLink) {
        Intent intent = new Intent(getActivity(), RouterActivity.class);
        intent.setData(Uri.parse(mediaLink));
        intent.putExtra(REQUEST_ACTION,REQUEST_DEFAULT);
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Retrieve the object from the Intent
            extractLink = data.getStringExtra(VIDEO_LINK);
            final String Name =data.getStringExtra(VIDEO_NAME);
            final String mimetype =data.getStringExtra(VIDEO_TYPE);
            final long duration =data.getLongExtra(VIDEO_DURATION,-1L);
            Log.e(TAG, "return back from RouteActivity = " + extractLink);
            Toast.makeText(getActivity(),Name+" with "+mimetype+ " length ="+duration,Toast.LENGTH_LONG).show();
        }
    }


    private void showVideoQualityDialog() {
        if (currentInfo == null) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.select_quality_external_players);
        builder.setNeutralButton(R.string.open_in_browser, (dialog, i) ->
                ShareUtils.openUrlInBrowser(requireActivity(), mediaLink));

        final List<VideoStream> videoStreamsForExternalPlayers =
                ListHelper.getSortedStreamVideosList(
                        activity,
                        getUrlAndNonTorrentStreams(currentInfo.getVideoStreams()),
                        getUrlAndNonTorrentStreams(currentInfo.getVideoOnlyStreams()),
                        false,
                        false
                );

        if (videoStreamsForExternalPlayers.isEmpty()) {
            builder.setMessage(R.string.no_video_streams_available_for_external_players);
            builder.setPositiveButton(R.string.ok, null);

        } else {
            final int selectedVideoStreamIndexForExternalPlayers =
                    ListHelper.getDefaultResolutionIndex(activity, videoStreamsForExternalPlayers);
            final CharSequence[] resolutions = videoStreamsForExternalPlayers.stream()
                    .map(VideoStream::getResolution).toArray(CharSequence[]::new);

            builder.setSingleChoiceItems(resolutions, selectedVideoStreamIndexForExternalPlayers,
                    null);
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (dialog, i) -> {
                final int index = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                // We don't have to manage the index validity because if there is no stream
                // available for external players, this code will be not executed and if there is
                // no stream which matches the default resolution, 0 is returned by
                // ListHelper.getDefaultResolutionIndex.
                // The index cannot be outside the bounds of the list as its always between 0 and
                // the list size - 1, .
                handleSelectedStream(videoStreamsForExternalPlayers.get(index));
            });
        }
        builder.show();
    }

    private void handleSelectedStream(VideoStream stream) {


    }

}
