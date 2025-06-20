import React, {useCallback, useEffect, useRef, useState} from 'react';

interface KnowledgeEntity {
    id: string;
    authors: string[];
    creationDate: number;
    issuerId: string;
    summary: string;
    title: string;
    type: string;
}


const Spinner: React.FC = () => (
    <div className="flex justify-center items-center p-8">
        <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
    </div>
);

const ErrorMessage: React.FC<{ message: string }> = ({message}) => (
    <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded-lg relative my-4" role="alert">
        <strong className="font-bold">Ошибка: </strong>
        <span className="block sm:inline">{message}</span>
    </div>
);

interface KnowledgeItemProps {
    item: KnowledgeEntity;
    onSelect: (id: string) => void;
}

const KnowledgeItem: React.FC<KnowledgeItemProps> = ({item, onSelect}) => (
    <div
        className="bg-white p-4 rounded-lg shadow-md hover:shadow-lg transition-shadow cursor-pointer border border-gray-200"
        onClick={() => onSelect(item.id)}
    >
        <h3 className="text-lg font-bold text-gray-800 truncate">{item.title}</h3>
        <p className="text-sm text-gray-600 mt-1">
            {item.authors.join(', ')} - <span className="text-gray-500">{item.creationDate}</span>
        </p>
        <p className="text-sm text-gray-700 mt-2 line-clamp-2">{item.summary}</p>
    </div>
);

interface KnowledgeDetailProps {
    item: KnowledgeEntity;
    recommendations: KnowledgeEntity[];
    onBack: () => void;
    onSelectRecommendation: (id: string) => void;
    isLoadingRecommendations: boolean;
}

const KnowledgeDetail: React.FC<KnowledgeDetailProps> = ({
                                                             item,
                                                             recommendations,
                                                             onBack,
                                                             onSelectRecommendation,
                                                             isLoadingRecommendations
                                                         }) => (
    <div className="bg-white p-6 rounded-lg shadow-xl animate-fade-in">
        <div className="flex justify-between items-start">
            <button onClick={onBack} className="mb-4 text-blue-600 hover:underline">
                &larr; К списку
            </button>
            <a
                href={`/api/v1/knowledge/${item.id}/download`}
                target="_blank"
                rel="noopener noreferrer"
                className="bg-purple-600 text-white font-bold px-4 py-2 rounded-md hover:bg-purple-700 transition-colors"
            >
                Скачать файл
            </a>
        </div>

        <h1 className="text-3xl font-bold text-gray-900 mt-2">{item.title}</h1>
        <p className="text-lg text-gray-600 mt-2">
            {item.authors.join(', ')}
        </p>
        <p className="text-md text-gray-500">{item.creationDate} &bull; {item.type}</p>

        <div className="mt-6 pt-6 border-t">
            <h2 className="text-xl font-semibold text-gray-800">Аннотация</h2>
            <p className="mt-2 text-gray-700 leading-relaxed whitespace-pre-wrap">{item.summary}</p>
        </div>

        <div className="mt-6 pt-6 border-t">
            <h2 className="text-xl font-semibold text-gray-800">Рекомендации</h2>
            {isLoadingRecommendations ? <Spinner/> : (
                <div className="mt-2 space-y-3">
                    {recommendations.length > 0 ? (
                        recommendations.map(rec => (
                            <div
                                key={rec.id}
                                className="bg-blue-50 p-3 rounded-md hover:bg-blue-100 cursor-pointer"
                                onClick={() => onSelectRecommendation(rec.id)}
                            >
                                <p className="font-semibold text-blue-800">{rec.title}</p>
                                <p className="text-sm text-blue-600">{rec.authors.join(', ')}</p>
                            </div>
                        ))
                    ) : <p className="text-gray-500">Рекомендации не найдены.</p>}
                </div>
            )}
        </div>
    </div>
);


function App() {

    const [knowledges, setKnowledges] = useState<KnowledgeEntity[]>([]);
    const [selectedKnowledge, setSelectedKnowledge] = useState<KnowledgeEntity | null>(null);
    const [recommendations, setRecommendations] = useState<KnowledgeEntity[]>([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [isLoadingRecommendations, setIsLoadingRecommendations] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const formRef = useRef<HTMLFormElement>(null);


    const fetchKnowledges = useCallback(async (query = '') => {
        setIsLoading(true);
        setError(null);
        try {
            const url = query ? `/api/v1/knowledge?search=${encodeURIComponent(query)}` : '/api/v1/knowledge';
            const response = await fetch(url);
            if (!response.ok) throw new Error('Не удалось получить данные с сервера.');
            const data = await response.json();
            setKnowledges(data);
        } catch (err: any) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    }, []);

    const fetchKnowledgeDetails = async (id: string) => {
        setIsLoading(true);
        setError(null);
        try {
            const response = await fetch(`/api/v1/knowledge/${id}`);
            if (!response.ok) throw new Error('Не удалось получить детали документа.');
            const data = await response.json();
            setSelectedKnowledge(data);
            fetchRecommendations(id);
        } catch (err: any) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    };

    const fetchRecommendations = async (id: string) => {
        setIsLoadingRecommendations(true);
        try {
            const response = await fetch(`/api/v1/knowledge/${id}/recommendations`);
            if (!response.ok) throw new Error('Не удалось загрузить рекомендации.');
            const data = await response.json();
            setRecommendations(data);
        } catch (err: any) {
            console.error("Не удалось загрузить рекомендации:", err.message);
            setRecommendations([]);
        } finally {
            setIsLoadingRecommendations(false);
        }
    };


    useEffect(() => {
        fetchKnowledges();
    }, [fetchKnowledges]);


    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchKnowledges(searchTerm);
    };

    const handleSelectKnowledge = (id: string) => {
        fetchKnowledgeDetails(id);
    };

    const handleBackToList = () => {
        setSelectedKnowledge(null);
        setRecommendations([]);
    };

    const handleCreateKnowledge = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        const urlInput = e.currentTarget.elements.namedItem('url') as HTMLInputElement;
        if (!urlInput || !urlInput.value) return;
        const url = urlInput.value;

        setIsLoading(true);
        setError(null);
        try {
            const response = await fetch('/api/v1/knowledge', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({url}),
            });
            const text = await response.text();
            if (!response.ok) {
                try {
                    const errJson = JSON.parse(text);
                    throw new Error(errJson.error || 'Произошла неизвестная ошибка.');
                } catch (parseError) {
                    throw new Error(text || 'Произошла неизвестная ошибка.');
                }
            }
            alert('Новый документ успешно добавлен!');
            formRef.current?.reset();
            setSearchTerm('');
            fetchKnowledges();
        } catch (err: any) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="bg-gray-50 min-h-screen font-sans text-gray-800">
            <header className="bg-white shadow-sm sticky top-0 z-10">
                <nav className="container mx-auto px-4 py-3 flex justify-between items-center">
                    <h1 className="text-2xl font-bold text-blue-600">Система Знаний</h1>
                    <div className="text-sm text-gray-500">Дипломный проект Камилы</div>
                </nav>
            </header>

            <main className="container mx-auto p-4 md:p-6">
                {error && <ErrorMessage message={error}/>}

                {selectedKnowledge ? (
                    <KnowledgeDetail
                        item={selectedKnowledge}
                        recommendations={recommendations}
                        onBack={handleBackToList}
                        onSelectRecommendation={handleSelectKnowledge}
                        isLoadingRecommendations={isLoadingRecommendations}
                    />
                ) : (
                    <>
                        <div className="bg-white p-4 rounded-lg shadow-md mb-6 border border-gray-200">
                            <form ref={formRef} onSubmit={handleCreateKnowledge}>
                                <label htmlFor="url" className="block text-lg font-semibold mb-2">Добавить новый
                                    документ по URL</label>
                                <div className="flex flex-col sm:flex-row gap-2">
                                    <input
                                        type="url"
                                        id="url"
                                        name="url"
                                        placeholder="https://dspace.kpfu.ru/xmlui/handle/net/..."
                                        className="flex-grow w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                                        required
                                    />
                                    <button
                                        type="submit"
                                        className="bg-blue-600 text-white font-bold px-4 py-2 rounded-md hover:bg-blue-700 transition-colors disabled:bg-gray-400"
                                        disabled={isLoading}
                                    >
                                        {isLoading ? 'Добавление...' : 'Добавить'}
                                    </button>
                                </div>
                            </form>
                        </div>

                        <div className="bg-white p-4 rounded-lg shadow-md border border-gray-200">
                            <form onSubmit={handleSearch}>
                                <label htmlFor="search" className="block text-lg font-semibold mb-2">Поиск
                                    документов</label>
                                <div className="flex flex-col sm:flex-row gap-2">
                                    <input
                                        type="search"
                                        id="search"
                                        value={searchTerm}
                                        onChange={(e) => setSearchTerm(e.target.value)}
                                        placeholder="Поиск по названию, аннотации или автору..."
                                        className="flex-grow w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    />
                                    <button type="submit"
                                            className="bg-green-600 text-white font-bold px-4 py-2 rounded-md hover:bg-green-700 transition-colors">Поиск
                                    </button>
                                </div>
                            </form>

                            <div className="mt-6">
                                {isLoading ? (
                                    <Spinner/>
                                ) : (
                                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                                        {knowledges.map(item => (
                                            <KnowledgeItem key={item.id} item={item} onSelect={handleSelectKnowledge}/>
                                        ))}
                                    </div>
                                )}
                                {knowledges.length === 0 && !isLoading && (
                                    <p className="text-center text-gray-500 py-8">Документы не найдены. Попробуйте
                                        другой запрос или добавьте новый документ.</p>
                                )}
                            </div>
                        </div>
                    </>
                )}
            </main>
        </div>
    );
}

export default App;
