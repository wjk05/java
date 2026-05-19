import { useState } from 'react';
import { documentApi } from '../api/document';
import { getErrorMessage } from '../api/request';
import FileUploadCard from '../components/FileUploadCard';

type AnalysisDifficulty = 'EASY' | 'NORMAL' | 'SHARP';

const ANALYSIS_DIFFICULTY_OPTIONS: {
  value: AnalysisDifficulty;
  label: string;
  desc: string;
}[] = [
  { value: 'EASY', label: '轻量分析', desc: '更快给出基础结构与内容建议' },
  { value: 'NORMAL', label: '标准分析', desc: '平衡速度与建议覆盖度' },
  { value: 'SHARP', label: '进阶分析', desc: '更强调深挖问题与改进建议' },
];

interface UploadPageProps {
  onUploadComplete: (resumeId: number) => void;
}

export default function UploadPage({ onUploadComplete }: UploadPageProps) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [profession, setProfession] = useState('');
  const [analysisDifficulty, setAnalysisDifficulty] = useState<AnalysisDifficulty>('NORMAL');

  const handleUpload = async (file: File) => {
    setUploading(true);
    setError('');

    try {
      const data = await documentApi.uploadAndAnalyze(file, {
        profession: profession.trim() || undefined,
        analysisDifficulty,
      });

      // 异步模式：只检查上传是否成功（storage 信息）
      if (!data.storage || !data.storage.resumeId) {
        throw new Error('上传失败，请重试');
      }

      // 上传成功，跳转到简历库（分析在后台进行）
      onUploadComplete(data.storage.resumeId);
    } catch (err) {
      setError(getErrorMessage(err));
      setUploading(false);
    }
  };

  return (
    <FileUploadCard
      title="开始文档分析"
      subtitle="上传职业文档，补充分析方向与分析力度，AI 将生成更贴近场景的分析结果"
      accept=".pdf,.doc,.docx,.txt"
      formatHint="支持 PDF, DOCX, TXT"
      maxSizeHint="最大 10MB"
      uploading={uploading}
      uploadButtonText="开始上传"
      selectButtonText="选择文档文件"
      error={error}
      onUpload={handleUpload}
      extraContent={(
        <div className="space-y-5">
          <div>
            <p className="text-sm font-semibold text-slate-700 dark:text-slate-200 mb-2">职业方向</p>
            <input
              type="text"
              value={profession}
              onChange={(event) => setProfession(event.target.value)}
              placeholder="例如：Java 后端、产品经理、运营、职业沟通表达"
              className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500/50"
            />
            <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">留空也可以上传；填写后会让分析更聚焦当前职业场景。</p>
          </div>

          <div>
            <p className="text-sm font-semibold text-slate-700 dark:text-slate-200 mb-3">分析力度</p>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              {ANALYSIS_DIFFICULTY_OPTIONS.map(option => {
                const selected = analysisDifficulty === option.value;
                return (
                  <button
                    key={option.value}
                    type="button"
                    onClick={() => setAnalysisDifficulty(option.value)}
                    className={`p-4 rounded-xl border-2 text-left transition-all ${selected
                      ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                      : 'border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600'
                    }`}
                  >
                    <p className={`text-sm font-semibold ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-800 dark:text-white'}`}>
                      {option.label}
                    </p>
                    <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{option.desc}</p>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}
    />
  );
}
