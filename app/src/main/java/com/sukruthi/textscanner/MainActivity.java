package com.sukruthi.textscanner;

import android.Manifest;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.aniketjain.textscanner.R;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_CODE = 100;
    private double budget = 0.0;
    private double cartAmount = 0.0;
    private double remainingBudget = 0.0;
    private boolean creditAdded = false;
    private boolean creditDialogShown = false;
    private DecimalFormat currencyFormat;

    private LinearLayout budgetEntryLayout;
    private EditText budgetEditText;
    private Button submitBudgetButton;
    private Button addCreditButton;
    private TextView cartTextView;
    private TextView scannedTextView; // Modified to use TextView for scanned text
    private Button captureBtn;
    private Button copyBtn;
    private double creditAmount = 0.0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        budgetEntryLayout = findViewById(R.id.budgetEntryLayout);
        budgetEditText = findViewById(R.id.budgetEditText);
        submitBudgetButton = findViewById(R.id.submitBudgetButton);
        addCreditButton = findViewById(R.id.addCreditButton);
        cartTextView = findViewById(R.id.cartTextView);
        scannedTextView = findViewById(R.id.scannedTextView); // Initialize scannedTextView
        captureBtn = findViewById(R.id.captureBtn);
        copyBtn = findViewById(R.id.copyBtn);

        currencyFormat = new DecimalFormat("#,##0.00");
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        currencyFormat.setDecimalFormatSymbols(symbols);

        budgetEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        submitBudgetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String budgetStr = budgetEditText.getText().toString();
                if (!budgetStr.isEmpty()) {
                    budget = Double.parseDouble(budgetStr);
                    remainingBudget = budget;
                    budgetEntryLayout.setVisibility(View.GONE);
                    updateBudgetDisplay();
                }
            }
        });

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissions();
            }
        });

        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String scannedText = scannedTextView.getText().toString();
                copyToClipboard(scannedText);
            }
        });

        addCreditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addToBudget(cartAmount);
                creditDialogShown = false;
                updateBudgetDisplay();
            }
        });

        budgetEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                addCreditButton.setVisibility(View.GONE);
                creditAdded = false;
                creditDialogShown = false;
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA
            }, REQUEST_CAMERA_CODE);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        CropImage.activity().setGuidelines(CropImageView.Guidelines.ON).start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri uri = (result != null) ? result.getUri() : null;
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                    getTextFromImage(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void getTextFromImage(Bitmap bitmap) {
        TextRecognizer recognizer = new TextRecognizer.Builder(this).build();
        if (!recognizer.isOperational()) {
            Toast.makeText(this, "Error!", Toast.LENGTH_SHORT).show();
        } else {
            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray<TextBlock> sparseArray = recognizer.detect(frame);
            StringBuilder stringBuilder = new StringBuilder();

            for (int i = 0; i < sparseArray.size(); i++) {
                TextBlock textBlock = sparseArray.valueAt(i);
                String text = textBlock.getValue();
                stringBuilder.append(text);
                stringBuilder.append("\n");

                double itemPrice = extractPrice(text);
                if (itemPrice > 0) {
                    addToCart(itemPrice);
                }
            }

            scannedTextView.setText(stringBuilder.toString());
            updateBudget(cartAmount);
        }
    }

    private double extractPrice(String text) {
        double price = 0.0;
        try {
            price = Double.parseDouble(text.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            // Handle parsing errors
        }
        return price;
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText("Copied text", text);
        clipboardManager.setPrimaryClip(clipData);

        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void addToCart(double itemPrice) {
        if (remainingBudget >= itemPrice) {
            cartAmount += itemPrice;
        } else {
            if (!creditDialogShown) {
                showCreditDialog();
                creditDialogShown = true;
            }
        }
        updateBudgetDisplay();
    }

    private void addToBudget(double amount) {
        remainingBudget += amount;
    }

    private void updateBudget(double spentAmount) {
        remainingBudget -= spentAmount;
        updateBudgetDisplay();
    }

    private void showCreditDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Budget Exceeded")
                .setMessage("The scanned amount exceeds the remaining budget. Do you want to add it to your budget?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Prompt the user for the credit amount
                        promptForCredit();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }

    private void promptForCredit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Credit")
                .setMessage("Enter the credit amount:")
                .setView(R.layout.dialog_credit_input) // Custom layout for credit input
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Dialog dialogView = (Dialog) dialog;
                        EditText creditInput = dialogView.findViewById(R.id.creditInput);

                        if (creditInput != null) {
                            String creditStr = creditInput.getText().toString();
                            if (!creditStr.isEmpty()) {
                                creditAmount = Double.parseDouble(creditStr);
                                addToBudget(creditAmount);  // Add the credit to the budget
                                updateBudgetDisplay();
                            }
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }

    private void updateBudgetDisplay() {
        DecimalFormat df = new DecimalFormat("#.##");
        cartTextView.setText("Cart: $" + df.format(cartAmount));

        // Check if the remaining budget is less than 0 and credit hasn't been added
        if (remainingBudget < 0 && !creditAdded && creditAmount <= 0) {
            showCreditDialog();
            creditAdded = true;  // Mark credit as added to avoid showing the pop-up again
        } else if (remainingBudget <= 20) {
            showNearBudgetDialog();
        }
    }


    private void showNearBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nearing Budget")
                .setMessage("You are nearing your budget. Remaining budget: $" + currencyFormat.format(remainingBudget))
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }
}
