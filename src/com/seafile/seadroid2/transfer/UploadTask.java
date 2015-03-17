package com.seafile.seadroid2.transfer;

import android.util.Log;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.ProgressMonitor;

import java.io.File;

/**
 * Upload task
 *
 */
public class UploadTask extends TransferTask {
    public static final String DEBUG_TAG = "UploadTask";

    private String dir;   // parent dir
    private String targetName;
    private boolean isUpdate;  // true if update an existing file
    private boolean isCopyToLocal; // false to turn off copy operation
    private UploadStateListener uploadStateListener;

    private DataManager dataManager;

    public UploadTask(int taskID, Account account, String repoID, String repoName,
                      String dir, String filePath, String targetName, boolean isUpdate,
                      boolean isCopyToLocal, UploadStateListener uploadStateListener) {
        super(taskID, account, repoName, repoID, filePath);
        this.dir = dir;
        this.isUpdate = isUpdate;
        this.isCopyToLocal = isCopyToLocal;
        this.uploadStateListener = uploadStateListener;
        this.targetName = targetName;

        this.totalSize = new File(filePath).length();
        this.finished = 0;

        this.dataManager = new DataManager(account);
    }

    public UploadTaskInfo getTaskInfo() {
        UploadTaskInfo info = new UploadTaskInfo(account, taskID, state, repoID,
                repoName, dir, path, isUpdate, isCopyToLocal,
                finished, totalSize, err);
        return info;
    }

    public void cancelUpload() {
        if (state != TaskState.INIT && state != TaskState.TRANSFERRING) {
            return;
        }
        state = TaskState.CANCELLED;
        super.cancel(true);
    }

    @Override
    protected void onPreExecute() {
        state = TaskState.TRANSFERRING;
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        long uploaded = values[0];
        Log.d(DEBUG_TAG, "Uploaded " + uploaded);
        this.finished = uploaded;
        uploadStateListener.onFileUploadProgress(taskID);
    }

    @Override
    protected File doInBackground(Void... params) {
        try {
            ProgressMonitor monitor = new ProgressMonitor() {
                @Override
                public void onProgressNotify(long uploaded) {
                    publishProgress(uploaded);
                }

                @Override
                public boolean isCancelled() {
                    return UploadTask.this.isCancelled();
                }
            };
            if (isUpdate) {
                dataManager.updateFile(repoName, repoID, dir, path, targetName, monitor, isCopyToLocal);
            } else {
                Log.d(DEBUG_TAG, "Upload path: " + path);
                dataManager.uploadFile(repoName, repoID, dir, path, targetName, monitor, isCopyToLocal);
            }
        } catch (SeafException e) {
            Log.d(DEBUG_TAG, "Upload exception " + e.getCode() + " " + e.getMessage());
            err = e;
        }

        return null;
    }

    @Override
    protected void onPostExecute(File file) {
        state = err == null ? TaskState.FINISHED : TaskState.FAILED;
        if (uploadStateListener != null) {
            if (err == null) {
                uploadStateListener.onFileUploaded(taskID);
            }
            else {
                uploadStateListener.onFileUploadFailed(taskID);
            }
        }
    }

    @Override
    protected void onCancelled() {
        if (uploadStateListener != null) {
            uploadStateListener.onFileUploadCancelled(taskID);
        }
        uploadStateListener.onFileUploadCancelled(taskID);
    }

    public String getDir() {
        return dir;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isCopyToLocal() {
        return isCopyToLocal;
    }

    public boolean isUpdate() {
        return isUpdate;
    }

}