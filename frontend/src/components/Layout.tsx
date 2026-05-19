import {Link, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {AnimatePresence, motion} from 'framer-motion';
import {
  AudioWaveform,
  Bot,
  CalendarDays,
  FileSearch,
  History,
  Library,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Sparkles,
  Sun,
  Zap,
} from 'lucide-react';
import {useTheme} from '../hooks/useTheme';
import {useState} from 'react';
import UnifiedInterviewModal, {type UnifiedInterviewConfig} from './UnifiedInterviewModal';

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description?: string;
}

interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
}

export default function Layout() {
  const location = useLocation();
  const currentPath = location.pathname;
  const {theme, toggleTheme} = useTheme();
  const navigate = useNavigate();
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [interviewModalPreset, setInterviewModalPreset] = useState<{
    defaultMode: 'text' | 'voice';
    defaultResumeId?: number;
    title: string;
    subtitle: string;
    startButtonText: string;
  } | null>(null);

  const openInterviewModalWithResume = (resumeId: number) => {
    setInterviewModalPreset({
      defaultMode: 'text',
      defaultResumeId: resumeId,
      title: '开始情景模拟',
      subtitle: '配置模拟参数，开始练习',
      startButtonText: '开始模拟',
    });
  };

  const handleInterviewStart = (config: UnifiedInterviewConfig) => {
    setInterviewModalPreset(null);
    if (config.mode === 'text') {
      navigate('/simulation/session', {
        state: {
          documentId: config.basedOnDocument ? config.resumeId : undefined,
          interviewConfig: {
            simulationDirection: config.simulationDirection,
            skillId: config.skillId,
            difficulty: config.difficulty,
            simulationDifficulty: config.simulationDifficulty,
            questionCount: config.questionCount,
            basedOnDocument: config.basedOnDocument,
            llmProvider: config.llmProvider,
            customCategories: config.customCategories,
            jdText: config.customJdText,
          },
        },
      });
      return;
    }

    const params = new URLSearchParams({
      skillId: config.skillId,
      difficulty: config.difficulty,
    });
    navigate(`/voice?${params.toString()}`, {
      state: {
        voiceConfig: {
          skillId: config.skillId,
          difficulty: config.difficulty,
          techEnabled: true,
            projectEnabled: true,
            hrEnabled: true,
            plannedDuration: config.plannedDuration,
            resumeId: config.basedOnDocument ? config.resumeId : undefined,
            llmProvider: config.llmProvider,
          },
        },
    });
  };

  // 按业务模块组织的导航项
  const navGroups: NavGroup[] = [
    {
      id: 'workspace',
      title: '工作台',
      items: [
        { id: 'documents', path: '/documents', label: '文档分析', icon: FileSearch, description: '管理文档，AI 分析' },
        { id: 'simulation', path: '/simulation', label: '情景模拟', icon: Zap, description: '多场景情景模拟练习' },
        { id: 'simulation-history', path: '/simulation/history', label: '模拟记录', icon: History, description: '查看练习历史记录' },
      ],
    },
    {
      id: 'knowledge',
      title: '知识与工具',
      items: [
        { id: 'kb-manage', path: '/knowledgebase', label: '知识库', icon: Library, description: '管理企业知识文档' },
        { id: 'chat', path: '/knowledgebase/chat', label: '问答助手', icon: Bot, description: '基于知识库智能问答' },
        { id: 'schedule', path: '/schedule', label: '场景日程', icon: CalendarDays, description: '管理日程安排' },
      ],
    },
    {
      id: 'voice',
      title: '语音交互',
      items: [
        { id: 'voice-simulation', path: '/voice', label: '语音对话', icon: AudioWaveform, description: '实时语音模拟对话' },
      ],
    },
  ];

  // 判断当前页面是否匹配导航项
  const isActive = (path: string) => {
    if (path.startsWith('#')) return false;
    if (path === '/documents') {
      return currentPath === '/documents'
        || currentPath === '/'
        || currentPath.startsWith('/documents/')
        || currentPath === '/documents/upload';
    }
    if (path === '/simulation') {
      return currentPath === '/simulation'
        || currentPath === '/simulation/session'
        || currentPath.startsWith('/simulation/session/');
    }
    if (path === '/simulation/history') {
      return currentPath.startsWith('/simulation/history');
    }
    if (path === '/voice') {
      return currentPath.startsWith('/voice');
    }
    if (path === '/knowledgebase') {
      return (currentPath === '/knowledgebase' || currentPath === '/knowledgebase/upload') && !currentPath.includes('/chat');
    }
    return currentPath.startsWith(path);
  };

  return (
    <div className="flex min-h-screen bg-slate-50 dark:bg-slate-950 transition-colors duration-300">
      {/* 左侧边栏 */}
      <aside 
        className={`fixed h-screen left-0 top-0 z-50 flex flex-col bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800 transition-all duration-300 ease-in-out shadow-xl ${
          isCollapsed ? 'w-16' : 'w-64'
        }`}
      >
        {/* 背景装饰 */}
        <div className="absolute inset-0 opacity-[0.03] dark:opacity-[0.05] pointer-events-none overflow-hidden">
          <div className="absolute -top-24 -left-24 w-64 h-64 bg-primary-500 rounded-full blur-3xl" />
          <div className="absolute top-1/2 -right-24 w-48 h-48 bg-teal-500 rounded-full blur-3xl" />
        </div>

        {/* Logo */}
        <div className={`p-6 flex items-center justify-between border-b border-slate-100 dark:border-slate-800/50 ${isCollapsed ? 'px-4' : ''}`}>
          <Link to="/documents" className="flex items-center gap-3 overflow-hidden">
            <div className="flex-shrink-0 w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
              <Sparkles className="w-5 h-5" />
            </div>
            {!isCollapsed && (
              <motion.div 
                initial={{ opacity: 0, x: -10 }}
                animate={{ opacity: 1, x: 0 }}
                className="flex flex-col"
              >
                <span className="text-lg font-bold text-slate-800 dark:text-white tracking-tight leading-tight">Ruici AI</span>
                <span className="text-[10px] text-slate-400 dark:text-slate-500 font-bold tracking-widest uppercase">智能分析平台</span>
              </motion.div>
            )}
          </Link>
        </div>

        {/* 折叠切换按钮 */}
        <button
          type="button"
          onClick={() => setIsCollapsed(!isCollapsed)}
          className="absolute -right-3 top-20 w-6 h-6 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-full flex items-center justify-center text-slate-500 dark:text-slate-400 shadow-sm hover:text-primary-500 transition-colors z-50"
        >
          {isCollapsed ? <PanelLeftOpen className="w-3.5 h-3.5" /> : <PanelLeftClose className="w-3.5 h-3.5" />}
        </button>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 overflow-y-auto scrollbar-none relative z-10">
          <div className="space-y-8">
            {navGroups.map((group) => (
              <div key={group.id}>
                {!isCollapsed && (
                  <div className="px-3 mb-3">
                    <span className="text-[11px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">
                      {group.title}
                    </span>
                  </div>
                )}
                <div className="space-y-1.5">
                  {group.items.map((item) => {
                    const active = isActive(item.path);

                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        title={isCollapsed ? item.label : undefined}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200
                          ${active
                            ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400 shadow-sm shadow-primary-500/5'
                            : 'text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800/50 hover:text-slate-900 dark:hover:text-white'
                          }`}
                      >
                        <div className={`flex-shrink-0 w-9 h-9 rounded-lg flex items-center justify-center transition-all duration-200
                          ${active
                            ? 'bg-primary-100 dark:bg-primary-900/40 text-primary-600 dark:text-primary-400'
                            : 'bg-slate-50 dark:bg-slate-800/50 text-slate-500 dark:text-slate-400 group-hover:bg-white dark:group-hover:bg-slate-700 group-hover:text-primary-500 dark:group-hover:text-primary-400 group-hover:shadow-sm'
                          }`}
                        >
                          <item.icon className={`w-5 h-5 ${active ? 'animate-pulse' : ''}`} />
                        </div>
                        {!isCollapsed && (
                          <div className="flex-1 min-w-0">
                            <span className={`text-sm block ${active ? 'font-bold' : 'font-medium'}`}>
                              {item.label}
                            </span>
                            {item.description && (
                              <span className="text-[10px] text-slate-400 dark:text-slate-500 truncate block font-medium mt-0.5">
                                {item.description}
                              </span>
                            )}
                          </div>
                        )}
                        {active && !isCollapsed && <div className="w-1.5 h-1.5 rounded-full bg-primary-500 shadow-glow shadow-primary-500" />}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </nav>

        {/* 底部信息与设置 */}
        <div className={`p-4 border-t border-slate-100 dark:border-slate-800/50 relative z-10 ${isCollapsed ? 'items-center' : ''}`}>
          <div className="flex items-center justify-between gap-2">
            {!isCollapsed && (
              <div className="px-3 py-1.5 bg-slate-50 dark:bg-slate-800/50 rounded-lg border border-slate-100 dark:border-slate-700/50">
                <p className="text-[10px] text-slate-500 dark:text-slate-400 font-bold flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-teal-500" />
                  Ruici AI v1.0
                </p>
              </div>
            )}
            
        <button
          type="button"
          onClick={toggleTheme}
              className={`flex items-center justify-center w-9 h-9 rounded-lg bg-slate-50 dark:bg-slate-800/50 text-slate-500 dark:text-slate-400 hover:bg-white dark:hover:bg-slate-700 hover:text-primary-500 dark:hover:text-primary-400 border border-slate-100 dark:border-slate-700/50 transition-all shadow-sm ${isCollapsed ? 'mx-auto' : ''}`}
              title={theme === 'dark' ? '切换为浅色模式' : '切换为深色模式'}
            >
              {theme === 'dark' ? <Sun className="w-4.5 h-4.5" /> : <Moon className="w-4.5 h-4.5" />}
            </button>
          </div>
        </div>
      </aside>

      {/* 主内容区 */}
      <main className={`flex-1 transition-all duration-300 min-h-screen flex flex-col ${isCollapsed ? 'ml-16' : 'ml-64'}`}>
        <div className="flex-1 p-6 md:p-10 relative">
          <AnimatePresence mode="wait">
            <motion.div
              key={currentPath}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.2, ease: "easeOut" }}
            >
              <Outlet context={{ openInterviewModalWithResume }} />
            </motion.div>
          </AnimatePresence>
        </div>
      </main>

      {/* 统一面试弹窗 */}
      <UnifiedInterviewModal
        isOpen={interviewModalPreset !== null}
        onClose={() => setInterviewModalPreset(null)}
        onStart={handleInterviewStart}
        defaultMode={interviewModalPreset?.defaultMode || 'text'}
        defaultResumeId={interviewModalPreset?.defaultResumeId}
        hideModeSwitch={interviewModalPreset?.defaultResumeId == null}
        title={interviewModalPreset?.title || '开始情景模拟'}
        subtitle={interviewModalPreset?.subtitle || '选择模拟模式和主题，快速开始'}
        startButtonText={interviewModalPreset?.startButtonText || '开始模拟'}
      />
    </div>
  );
}
