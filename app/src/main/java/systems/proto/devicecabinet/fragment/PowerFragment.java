package systems.proto.devicecabinet.fragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import systems.proto.devicecabinet.R;
import systems.proto.devicecabinet.activity.ConfigActivity;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PowerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PowerFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private ImageView barcodeImage;
    private ProgressBar progress;
    private TextView infoTF;
    private View rootView;

    public PowerFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment PowerFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static PowerFragment newInstance(String param1, String param2) {
        PowerFragment fragment = new PowerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_power, container, false);
        progress = (ProgressBar) view.findViewById(R.id.levelPB);
        infoTF = (TextView) view.findViewById(R.id.infoTF);
        barcodeImage = (ImageView) view.findViewById(R.id.barcode_image);
        rootView = infoTF.getRootView();

        barcodeImage.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Intent i = new Intent(getActivity(),ConfigActivity.class);
                startActivity(i);
                return false;
            }
        });

        return view;
    }

    public void setDeviceId(String bc) {

        infoTF.setText(bc);

        QRCodeWriter bw = new QRCodeWriter();
        try {
            BitMatrix bm = bw.encode(bc, BarcodeFormat.QR_CODE, 300, 300);
            int[] a = new int[bm.getHeight() * bm.getWidth()];
            int z = 0;
            for (int h = 0; h < bm.getHeight(); h++) {
                for (int w = 0; w < bm.getWidth(); w++, z++) {
                    a[z] = bm.get(w, h)
                            ? ContextCompat.getColor(getActivity(), R.color.black)
                            : ContextCompat.getColor(getActivity(), R.color.white);
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888);

            bitmap.setPixels(a, 0, 300, 0, 0,  bm.getWidth(), bm.getHeight());
            barcodeImage.setImageBitmap(bitmap);
        }
        catch (WriterException ex) {
            ex.printStackTrace();
        }

    }

    public void showPower(int level, int scale) {

        if (getActivity()==null) {
            return;
        }
        getActivity().setVisible(true);

        float batteryPct = level * 100 / (float) scale;
        progress.setMax(scale);
        progress.setProgress(level);
        if (batteryPct < 50) {
            rootView.setBackgroundColor(Color.rgb(255, 0, 0));
        } else if (batteryPct > 90) {
            rootView.setBackgroundColor(Color.rgb(0, 255, 0));
        } else {
            rootView.setBackgroundColor(Color.rgb(255, 165, 0));
        }

    }

    public void screenOff() {

        rootView.setBackgroundColor(Color.BLACK);
        getActivity().setVisible(false);

    }
}